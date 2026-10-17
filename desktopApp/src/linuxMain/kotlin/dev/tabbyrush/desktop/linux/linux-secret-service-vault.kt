package dev.tabbyrush.desktop.linux

import dev.tabbyrush.desktop.CredentialVaultProvider
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.contract.VaultStatus
import dev.tabbyrush.shared.data.JvmAccountFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Serializable
internal data class StoredCredentialPayload(
    val accessToken: String,
    val refreshToken: String
)

interface SecretServiceClient {
    fun checkStatus(): VaultStatus
    fun storeSecret(service: String, account: String, label: String, secret: String)
    fun lookupSecret(service: String, account: String): String?
    fun clearSecret(service: String, account: String)
}

/**
 * Implementation of SecretServiceClient using the freedesktop secret-tool CLI.
 */
class ProcessSecretServiceClient : SecretServiceClient {
    override fun checkStatus(): VaultStatus {
        val dbusAddress = System.getenv("DBUS_SESSION_BUS_ADDRESS")
        val display = System.getenv("DISPLAY")
        if (dbusAddress.isNullOrBlank() && display.isNullOrBlank()) {
            return VaultStatus.UNAVAILABLE
        }
        return try {
            val process = ProcessBuilder("secret-tool", "search", "--all", "service", "tabbyrush-probe")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return VaultStatus.UNAVAILABLE
            }
            val exitCode = process.exitValue()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            when {
                output.contains("locked", ignoreCase = true) -> VaultStatus.LOCKED
                exitCode == 0 || exitCode == 1 -> VaultStatus.READY
                else -> VaultStatus.UNAVAILABLE
            }
        } catch (_: Exception) {
            VaultStatus.UNAVAILABLE
        }
    }

    override fun storeSecret(service: String, account: String, label: String, secret: String) {
        val process = ProcessBuilder(
            "secret-tool", "store",
            "--label=$label",
            "service", service,
            "account", account
        ).start()

        process.outputStream.bufferedWriter().use { writer ->
            writer.write(secret)
            writer.flush()
        }

        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Secret Service store timed out")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val err = process.errorStream.bufferedReader().use { it.readText() }
            throw IllegalStateException("Failed to store credentials in Secret Service (exit $exitCode): $err")
        }
    }

    override fun lookupSecret(service: String, account: String): String? {
        val process = ProcessBuilder(
            "secret-tool", "lookup",
            "service", service,
            "account", account
        ).start()

        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Secret Service lookup timed out")
        }
        val exitCode = process.exitValue()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (exitCode == 1) {
            return null // Not found
        }
        if (exitCode != 0) {
            val err = process.errorStream.bufferedReader().use { it.readText() }
            throw IllegalStateException("Secret Service lookup failed (exit $exitCode): $err")
        }
        return if (output.isBlank()) null else output.trim()
    }

    override fun clearSecret(service: String, account: String) {
        val process = ProcessBuilder(
            "secret-tool", "clear",
            "service", service,
            "account", account
        ).start()
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Secret Service clear timed out")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0 && exitCode != 1) {
            val err = process.errorStream.bufferedReader().use { it.readText() }
            throw IllegalStateException("Failed to clear secret in Secret Service (exit $exitCode): $err")
        }
    }
}

/**
 * LinuxSecretServiceVault implements hardware/OS-backed credential custody via Freedesktop Secret Service.
 *
 * Invariants:
 * 1. Tokens are serialized together as a single atomic credential bundle to prevent partial updates.
 * 2. Fail-closed: Never falls back to plaintext file storage.
 * 3. Status transitions faithfully represent daemon availability, locks, or payload corruption.
 */
class LinuxSecretServiceVault(
    private val client: SecretServiceClient = ProcessSecretServiceClient()
) : CredentialVault {

    private val json = Json { ignoreUnknownKeys = true }

    override val status: VaultStatus
        get() = client.checkStatus()

    override suspend fun load(accountKey: AccountKey): CredentialBundle? = withContext(Dispatchers.IO) {
        val raw = client.lookupSecret(SERVICE_NAME, accountKey.value) ?: return@withContext null
        try {
            val payload = json.decodeFromString<StoredCredentialPayload>(raw)
            CredentialBundle(accessToken = payload.accessToken, refreshToken = payload.refreshToken)
        } catch (e: Exception) {
            throw IllegalStateException("Corrupt credential bundle in Secret Service for account: ${accountKey.value}", e)
        }
    }

    override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle): Unit = withContext(Dispatchers.IO) {
        val payload = StoredCredentialPayload(
            accessToken = bundle.accessToken,
            refreshToken = bundle.refreshToken
        )
        val raw = json.encodeToString(StoredCredentialPayload.serializer(), payload)
        client.storeSecret(
            service = SERVICE_NAME,
            account = accountKey.value,
            label = "TabbyRush ($SERVICE_NAME:${accountKey.value})",
            secret = raw
        )
    }

    override suspend fun clear(accountKey: AccountKey): Unit = withContext(Dispatchers.IO) {
        client.clearSecret(SERVICE_NAME, accountKey.value)
    }

    companion object {
        const val SERVICE_NAME = "tabbyrush"
    }
}

/**
 * SPI Provider for Linux Secret Service vault.
 */
class LinuxSecretServiceVaultProvider : CredentialVaultProvider {
    override val target: String = "linux"

    override fun createVault(accountFiles: JvmAccountFiles): CredentialVault {
        return LinuxSecretServiceVault()
    }
}
