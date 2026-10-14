package dev.doggo.desktop.windows

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import dev.doggo.desktop.CredentialVaultProvider
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.CredentialVault
import dev.doggo.shared.contract.VaultStatus
import dev.doggo.shared.data.JvmAccountFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

@Serializable
internal data class StoredCredentialPayload(
    val accountKey: String,
    val accessToken: String,
    val refreshToken: String
)

/**
 * Interface abstracting Windows DPAPI data protection operations for testability.
 */
interface WindowsDpapiProtector {
    fun isAvailable(): Boolean
    fun protect(plainBytes: ByteArray): ByteArray
    fun unprotect(cipherBytes: ByteArray): ByteArray
}

/**
 * Default Windows DPAPI protector using JNA Crypt32Util.
 * Invariants:
 * - Scope: Always current user scope (CRYPTPROTECT_LOCAL_MACHINE is NEVER used).
 * - UI Forbidden: CRYPTPROTECT_UI_FORBIDDEN flag set to prevent interactive prompts.
 * - Fails closed: CryptUnprotectData failure throws Win32Exception.
 */
class JnaWindowsDpapiProtector : WindowsDpapiProtector {
    override fun isAvailable(): Boolean {
        return Platform.isWindows()
    }

    override fun protect(plainBytes: ByteArray): ByteArray {
        if (!isAvailable()) {
            throw IllegalStateException("Windows DPAPI is only available on Windows operating systems")
        }
        return Crypt32Util.cryptProtectData(plainBytes, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
    }

    override fun unprotect(cipherBytes: ByteArray): ByteArray {
        if (!isAvailable()) {
            throw IllegalStateException("Windows DPAPI is only available on Windows operating systems")
        }
        return Crypt32Util.cryptUnprotectData(cipherBytes, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
    }
}

/**
 * WindowsDpapiVault implements hardware/OS-backed credential custody on Windows via DPAPI (Data Protection API).
 *
 * Invariants:
 * 1. Tokens are serialized together with the account key as a single atomic credential bundle to prevent partial updates.
 * 2. User-Scoped DPAPI: DPAPI key derivation is bound to the current Windows user login credentials.
 * 3. Atomic Replacement: Bundles are written to a temp file, user ACL is enforced, and atomically moved into place.
 * 4. Fail-Closed: Never falls back to plaintext storage or machine-wide encryption.
 * 5. Memory Hygiene: Native and JVM byte arrays holding plaintext tokens are zeroed out after use.
 * 6. Status Transitions: Corrupt files or unprotect failures transition status to CORRUPT and preserve local databases.
 */
class WindowsDpapiVault(
    private val accountFiles: JvmAccountFiles,
    private val protector: WindowsDpapiProtector = JnaWindowsDpapiProtector()
) : CredentialVault {

    private val json = Json { ignoreUnknownKeys = true }
    private var internalStatus: VaultStatus = if (protector.isAvailable()) VaultStatus.READY else VaultStatus.UNAVAILABLE

    override val status: VaultStatus
        get() = internalStatus

    fun getVaultFile(accountKey: AccountKey): File {
        val dir = accountFiles.getAccountDirectory(accountKey)
        return File(dir, VAULT_FILE_NAME)
    }

    override suspend fun load(accountKey: AccountKey): CredentialBundle? = withContext(Dispatchers.IO) {
        if (!protector.isAvailable()) {
            internalStatus = VaultStatus.UNAVAILABLE
            throw IllegalStateException("Windows DPAPI provider is unavailable on this host")
        }

        val vaultFile = getVaultFile(accountKey)
        if (!vaultFile.exists() || vaultFile.length() == 0L) {
            return@withContext null
        }

        val cipherBytes = try {
            vaultFile.readBytes()
        } catch (e: Exception) {
            internalStatus = VaultStatus.CORRUPT
            throw IllegalStateException("Failed to read DPAPI vault file for account: ${accountKey.value}", e)
        }

        val plainBytes = try {
            protector.unprotect(cipherBytes)
        } catch (e: Exception) {
            internalStatus = VaultStatus.CORRUPT
            throw IllegalStateException("Failed to unprotect DPAPI credential bundle for account: ${accountKey.value}", e)
        }

        try {
            val rawJson = plainBytes.toString(Charsets.UTF_8)
            val payload = json.decodeFromString<StoredCredentialPayload>(rawJson)

            if (payload.accountKey.isNotBlank() && payload.accountKey != accountKey.value) {
                internalStatus = VaultStatus.CORRUPT
                throw IllegalStateException(
                    "Account key mismatch in DPAPI bundle: expected '${accountKey.value}', found '${payload.accountKey}'"
                )
            }

            internalStatus = VaultStatus.READY
            CredentialBundle(accessToken = payload.accessToken, refreshToken = payload.refreshToken)
        } catch (e: Exception) {
            internalStatus = VaultStatus.CORRUPT
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Corrupt DPAPI credential payload for account: ${accountKey.value}", e)
        } finally {
            plainBytes.fill(0)
        }
    }

    override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle): Unit = withContext(Dispatchers.IO) {
        if (!protector.isAvailable()) {
            internalStatus = VaultStatus.UNAVAILABLE
            throw IllegalStateException("Windows DPAPI provider is unavailable on this host")
        }

        val dir = accountFiles.getAccountDirectory(accountKey)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val payload = StoredCredentialPayload(
            accountKey = accountKey.value,
            accessToken = bundle.accessToken,
            refreshToken = bundle.refreshToken
        )
        val rawJson = json.encodeToString(StoredCredentialPayload.serializer(), payload)
        val plainBytes = rawJson.toByteArray(Charsets.UTF_8)

        val cipherBytes = try {
            protector.protect(plainBytes)
        } finally {
            plainBytes.fill(0)
        }

        val vaultFile = File(dir, VAULT_FILE_NAME)
        val tempFile = File(dir, "$VAULT_FILE_NAME.tmp")

        try {
            tempFile.writeBytes(cipherBytes)
            applyRestrictiveUserAcl(tempFile)
            Files.move(
                tempFile.toPath(),
                vaultFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            internalStatus = VaultStatus.READY
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw IllegalStateException("Failed to atomically write DPAPI vault file for account: ${accountKey.value}", e)
        }
    }

    override suspend fun clear(accountKey: AccountKey): Unit = withContext(Dispatchers.IO) {
        val dir = accountFiles.getAccountDirectory(accountKey)
        val vaultFile = File(dir, VAULT_FILE_NAME)
        val tempFile = File(dir, "$VAULT_FILE_NAME.tmp")

        if (tempFile.exists()) {
            Files.deleteIfExists(tempFile.toPath())
        }
        if (vaultFile.exists()) {
            Files.deleteIfExists(vaultFile.toPath())
        }
        internalStatus = if (protector.isAvailable()) VaultStatus.READY else VaultStatus.UNAVAILABLE
    }

    private fun applyRestrictiveUserAcl(file: File) {
        val path = file.toPath()
        val isAclSupported = path.fileSystem.supportedFileAttributeViews().contains("acl")
        if (isAclSupported) {
            val aclView = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                ?: throw IllegalStateException("AclFileAttributeView unavailable on ACL-supporting filesystem for: ${file.name}")
            val owner = aclView.owner
                ?: throw IllegalStateException("Unable to resolve owner principal for: ${file.name}")
            val userEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                    AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA,
                    AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.READ_NAMED_ATTRS,
                    AclEntryPermission.WRITE_NAMED_ATTRS,
                    AclEntryPermission.EXECUTE,
                    AclEntryPermission.DELETE_CHILD,
                    AclEntryPermission.READ_ATTRIBUTES,
                    AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.DELETE,
                    AclEntryPermission.READ_ACL,
                    AclEntryPermission.WRITE_ACL,
                    AclEntryPermission.WRITE_OWNER,
                    AclEntryPermission.SYNCHRONIZE
                )
                .build()
            try {
                aclView.acl = listOf(userEntry)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to apply restrictive user ACL on: ${file.name}", e)
            }
        } else if (Platform.isWindows()) {
            throw IllegalStateException("ACL file attribute view is required on Windows for: ${file.name}")
        }
    }

    companion object {
        const val VAULT_FILE_NAME = "credentials.dpapi"
    }
}

/**
 * Service Provider Interface implementation for Windows DPAPI vault.
 */
class WindowsDpapiVaultProvider : CredentialVaultProvider {
    override val target: String = "windows"

    override fun createVault(accountFiles: JvmAccountFiles): CredentialVault {
        return WindowsDpapiVault(accountFiles)
    }
}
