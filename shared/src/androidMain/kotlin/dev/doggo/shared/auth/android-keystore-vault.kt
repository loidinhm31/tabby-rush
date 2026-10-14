package dev.doggo.shared.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.CredentialVault
import dev.doggo.shared.contract.VaultStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore implementation of CredentialVault.
 *
 * Security and Integrity Invariants:
 * - Uses hardware-backed Android KeyStore AES-256-GCM.
 * - Generates a fresh 12-byte secure random nonce for every encryption operation.
 * - Binds ciphertext to format version and exact AccountKey via Authenticated Additional Data (AAD).
 * - Performs atomic private file writes using temporary files and atomic rename.
 * - Synchronized via Mutex to prevent race conditions on temp files.
 * - Distinguishes READY, LOCKED, CORRUPT, and UNAVAILABLE states without implicit DB or vault wipes.
 * - Never logs or exposes plaintext tokens or secrets.
 */
class AndroidKeystoreVault(
    private val context: Context,
    private val keyStoreProvider: String = "AndroidKeyStore"
) : CredentialVault {

    companion object {
        private const val MAGIC_HEADER = 0x44475631 // "DGV1"
        private const val GCM_NONCE_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_ALIAS_PREFIX = "doggo_vault_key_"
        private const val VAULT_DIR = "doggo_vault"
    }

    private val vaultMutex = Mutex()

    private var _status: VaultStatus = VaultStatus.READY
    override val status: VaultStatus
        get() = _status

    private val secureRandom = SecureRandom()

    private val vaultDirectory: File
        get() = File(context.filesDir, VAULT_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private fun getVaultFile(accountKey: AccountKey): File {
        return File(vaultDirectory, "account_${accountKey.value}.vault")
    }

    private fun getKeyAlias(accountKey: AccountKey): String {
        return "$KEY_ALIAS_PREFIX${accountKey.value}"
    }

    private fun getSecretKey(accountKey: AccountKey): SecretKey? {
        val alias = getKeyAlias(accountKey)
        val keyStore = KeyStore.getInstance(keyStoreProvider).apply { load(null) }

        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }
        return null
    }

    private fun getOrCreateSecretKey(accountKey: AccountKey): SecretKey {
        val existing = getSecretKey(accountKey)
        if (existing != null) {
            return existing
        }

        val alias = getKeyAlias(accountKey)
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreProvider)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // Nonce managed explicitly with SecureRandom
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun computeAad(accountKey: AccountKey): ByteArray {
        return "doggo_vault_v1:${accountKey.value}".encodeToByteArray()
    }

    override suspend fun load(accountKey: AccountKey): CredentialBundle? = vaultMutex.withLock {
        withContext(Dispatchers.IO) {
            val file = getVaultFile(accountKey)
            if (!file.exists()) {
                _status = VaultStatus.READY
                return@withContext null
            }

            val fileBytes = try {
                file.readBytes()
            } catch (e: Exception) {
                _status = VaultStatus.UNAVAILABLE
                throw IllegalStateException("Unable to read vault file for account: file I/O error", e)
            }

            if (fileBytes.size < 4 + GCM_NONCE_LENGTH + (GCM_TAG_LENGTH_BITS / 8)) {
                _status = VaultStatus.CORRUPT
                throw IllegalStateException("Vault file is corrupted (truncated header)")
            }

            val buffer = ByteBuffer.wrap(fileBytes)
            val magic = buffer.int
            if (magic != MAGIC_HEADER) {
                _status = VaultStatus.CORRUPT
                throw IllegalStateException("Vault file header magic mismatch (corrupt format)")
            }

            val nonce = ByteArray(GCM_NONCE_LENGTH)
            buffer.get(nonce)

            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            val secretKey = getSecretKey(accountKey)
            if (secretKey == null) {
                _status = VaultStatus.UNAVAILABLE
                throw IllegalStateException("KeyStore key unavailable for existing vault file")
            }

            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                cipher.updateAAD(computeAad(accountKey))

                val plaintext = cipher.doFinal(ciphertext)
                val plainBuffer = ByteBuffer.wrap(plaintext)

                val accessLen = plainBuffer.int
                if (accessLen < 0 || accessLen > plainBuffer.remaining()) {
                    _status = VaultStatus.CORRUPT
                    throw IllegalStateException("Corrupt plaintext bundle format")
                }
                val accessBytes = ByteArray(accessLen)
                plainBuffer.get(accessBytes)

                val refreshLen = plainBuffer.int
                if (refreshLen < 0 || refreshLen > plainBuffer.remaining()) {
                    _status = VaultStatus.CORRUPT
                    throw IllegalStateException("Corrupt plaintext bundle format")
                }
                val refreshBytes = ByteArray(refreshLen)
                plainBuffer.get(refreshBytes)

                val accessToken = accessBytes.decodeToString()
                val refreshToken = refreshBytes.decodeToString()

                _status = VaultStatus.READY
                CredentialBundle(accessToken, refreshToken)
            } catch (e: KeyPermanentlyInvalidatedException) {
                _status = VaultStatus.LOCKED
                throw IllegalStateException("KeyStore key permanently invalidated (biometric or lock change)", e)
            } catch (e: UserNotAuthenticatedException) {
                _status = VaultStatus.LOCKED
                throw IllegalStateException("User authentication required to unlock KeyStore key", e)
            } catch (e: Exception) {
                _status = VaultStatus.CORRUPT
                throw IllegalStateException("Failed to decrypt vault bundle", e)
            }
        }
    }

    override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle): Unit = vaultMutex.withLock {
        withContext(Dispatchers.IO) {
            val nonce = ByteArray(GCM_NONCE_LENGTH)
            secureRandom.nextBytes(nonce)

            val accessBytes = bundle.accessToken.encodeToByteArray()
            val refreshBytes = bundle.refreshToken.encodeToByteArray()

            val plaintextLen = 4 + accessBytes.size + 4 + refreshBytes.size
            val plainBuffer = ByteBuffer.allocate(plaintextLen)
            plainBuffer.putInt(accessBytes.size)
            plainBuffer.put(accessBytes)
            plainBuffer.putInt(refreshBytes.size)
            plainBuffer.put(refreshBytes)
            val plaintext = plainBuffer.array()

            val ciphertext = try {
                val secretKey = getOrCreateSecretKey(accountKey)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
                cipher.updateAAD(computeAad(accountKey))
                cipher.doFinal(plaintext)
            } catch (e: KeyPermanentlyInvalidatedException) {
                _status = VaultStatus.LOCKED
                throw IllegalStateException("Cannot store in locked vault: Key permanently invalidated", e)
            } catch (e: Exception) {
                _status = VaultStatus.UNAVAILABLE
                throw IllegalStateException("Encryption failed", e)
            }

            val totalLen = 4 + GCM_NONCE_LENGTH + ciphertext.size
            val fileBuffer = ByteBuffer.allocate(totalLen)
            fileBuffer.putInt(MAGIC_HEADER)
            fileBuffer.put(nonce)
            fileBuffer.put(ciphertext)

            val targetFile = getVaultFile(accountKey)
            val tempFile = File.createTempFile("vault_${accountKey.value}_", ".tmp", vaultDirectory)

            try {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(fileBuffer.array())
                    fos.flush()
                    fos.fd.sync()
                }
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                _status = VaultStatus.READY
            } catch (e: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                _status = VaultStatus.UNAVAILABLE
                throw IllegalStateException("Atomic write to vault file failed", e)
            }
        }
    }

    override suspend fun clear(accountKey: AccountKey): Unit = vaultMutex.withLock {
        withContext(Dispatchers.IO) {
            val targetFile = getVaultFile(accountKey)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            try {
                val keyStore = KeyStore.getInstance(keyStoreProvider).apply { load(null) }
                val alias = getKeyAlias(accountKey)
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
            } catch (_: Exception) {
                // Best effort KeyStore alias removal
            }
            _status = VaultStatus.READY
        }
    }
}
