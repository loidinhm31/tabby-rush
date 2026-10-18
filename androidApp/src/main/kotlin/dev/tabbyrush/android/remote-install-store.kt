package dev.tabbyrush.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class PendingInstallCommand {
    abstract val generation: Long
    abstract val accountKey: String
    abstract val userId: String

    data class Bind(
        override val generation: Long,
        override val accountKey: String,
        override val userId: String,
        val token: String,
        val appVersion: String
    ) : PendingInstallCommand()

    data class Unbind(
        override val generation: Long,
        override val accountKey: String,
        override val userId: String,
        val expectedBindingGeneration: Long,
        val capturedAccessToken: String? = null
    ) : PendingInstallCommand()
}

data class RemoteInstallState(
    val installId: String,
    val installProof: String,
    val persistedGeneration: Long = 0L,
    val lastBoundGeneration: Long? = null,
    val cachedFcmToken: String? = null,
    val pendingCommand: PendingInstallCommand? = null
)

interface RemoteInstallStore {
    suspend fun getOrCreateIdentity(): Pair<String, String>
    suspend fun readState(): RemoteInstallState
    suspend fun writeState(state: RemoteInstallState)
    suspend fun resetIdentity(): Pair<String, String>
}

object RemoteInstallStateJson {
    fun toJson(state: RemoteInstallState): String {
        val obj = JSONObject()
        obj.put("installId", state.installId)
        obj.put("installProof", state.installProof)
        obj.put("persistedGeneration", state.persistedGeneration)
        state.lastBoundGeneration?.let { obj.put("lastBoundGeneration", it) }
        state.cachedFcmToken?.let { obj.put("cachedFcmToken", it) }
        state.pendingCommand?.let { cmd ->
            val cmdObj = JSONObject()
            cmdObj.put("generation", cmd.generation)
            cmdObj.put("accountKey", cmd.accountKey)
            cmdObj.put("userId", cmd.userId)
            when (cmd) {
                is PendingInstallCommand.Bind -> {
                    cmdObj.put("kind", "bind")
                    cmdObj.put("token", cmd.token)
                    cmdObj.put("appVersion", cmd.appVersion)
                }
                is PendingInstallCommand.Unbind -> {
                    cmdObj.put("kind", "unbind")
                    cmdObj.put("expectedBindingGeneration", cmd.expectedBindingGeneration)
                    cmd.capturedAccessToken?.let { cmdObj.put("capturedAccessToken", it) }
                }
            }
            obj.put("pendingCommand", cmdObj)
        }
        return obj.toString()
    }

    fun fromJson(jsonStr: String): RemoteInstallState {
        val obj = JSONObject(jsonStr)
        val installId = obj.getString("installId")
        val installProof = obj.getString("installProof")
        val persistedGeneration = obj.optLong("persistedGeneration", 0L)
        val lastBoundGeneration = if (obj.has("lastBoundGeneration")) obj.getLong("lastBoundGeneration") else null
        val cachedFcmToken = if (obj.has("cachedFcmToken")) obj.getString("cachedFcmToken") else null
        val pendingCommand = if (obj.has("pendingCommand")) {
            val cmdObj = obj.getJSONObject("pendingCommand")
            val kind = cmdObj.optString("kind")
            val gen = cmdObj.getLong("generation")
            val accKey = cmdObj.getString("accountKey")
            val uId = cmdObj.getString("userId")
            if (kind == "unbind") {
                val expGen = cmdObj.getLong("expectedBindingGeneration")
                val token = if (cmdObj.has("capturedAccessToken")) cmdObj.getString("capturedAccessToken") else null
                PendingInstallCommand.Unbind(gen, accKey, uId, expGen, token)
            } else {
                val tok = cmdObj.getString("token")
                val ver = cmdObj.optString("appVersion", "1.0.0")
                PendingInstallCommand.Bind(gen, accKey, uId, tok, ver)
            }
        } else null

        return RemoteInstallState(
            installId = installId,
            installProof = installProof,
            persistedGeneration = persistedGeneration,
            lastBoundGeneration = lastBoundGeneration,
            cachedFcmToken = cachedFcmToken,
            pendingCommand = pendingCommand
        )
    }
}

class InMemoryRemoteInstallStore(
    initialState: RemoteInstallState? = null
) : RemoteInstallStore {
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()
    private var state: RemoteInstallState? = initialState

    override suspend fun getOrCreateIdentity(): Pair<String, String> = mutex.withLock {
        state?.let { return Pair(it.installId, it.installProof) }
        val id = UUID.randomUUID().toString()
        val proofBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes)
        val newState = RemoteInstallState(installId = id, installProof = proof)
        state = newState
        Pair(id, proof)
    }

    override suspend fun resetIdentity(): Pair<String, String> = mutex.withLock {
        val id = UUID.randomUUID().toString()
        val proofBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes)
        val newState = RemoteInstallState(installId = id, installProof = proof)
        state = newState
        Pair(id, proof)
    }

    override suspend fun readState(): RemoteInstallState = mutex.withLock {
        state ?: run {
            val id = UUID.randomUUID().toString()
            val proofBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
            val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes)
            val newState = RemoteInstallState(installId = id, installProof = proof)
            state = newState
            newState
        }
    }

    override suspend fun writeState(state: RemoteInstallState): Unit = mutex.withLock {
        this.state = state
    }
}

class EncryptedRemoteInstallStore(
    private val context: Context,
    private val storageDir: File = context.noBackupFilesDir,
    private val keyStoreProvider: String = "AndroidKeyStore"
) : RemoteInstallStore {

    companion object {
        private const val KEY_ALIAS = "tabbyrush_remote_install_v1"
        private const val FILE_NAME = "remote_install_v1.enc"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
        private val AAD_BYTES = "tabbyrush_remote_install_v1".encodeToByteArray()
    }

    private val mutex = Mutex()
    private val secureRandom = SecureRandom()

    private val storeFile: File
        get() = File(storageDir, FILE_NAME)

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(keyStoreProvider).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateSecretKey(): SecretKey {
        getSecretKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreProvider)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false)
                .build()
        )
        return keyGenerator.generateKey()
    }

    override suspend fun getOrCreateIdentity(): Pair<String, String> = mutex.withLock {
        val state = readStateInternal()
        if (state != null) {
            return Pair(state.installId, state.installProof)
        }
        val newId = UUID.randomUUID().toString()
        val proofBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes)
        val newState = RemoteInstallState(installId = newId, installProof = proof)
        writeStateInternal(newState)
        Pair(newId, proof)
    }

    override suspend fun resetIdentity(): Pair<String, String> = mutex.withLock {
        val newId = UUID.randomUUID().toString()
        val proofBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes)
        val newState = RemoteInstallState(installId = newId, installProof = proof)
        writeStateInternal(newState)
        Pair(newId, proof)
    }

    override suspend fun readState(): RemoteInstallState = mutex.withLock {
        readStateInternal() ?: run {
            val newId = UUID.randomUUID().toString()
            val proofBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
            val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofBytes)
            val newState = RemoteInstallState(installId = newId, installProof = proof)
            writeStateInternal(newState)
            newState
        }
    }

    override suspend fun writeState(state: RemoteInstallState): Unit = mutex.withLock {
        writeStateInternal(state)
    }

    private suspend fun readStateInternal(): RemoteInstallState? = withContext(Dispatchers.IO) {
        val file = storeFile
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            val key = getSecretKey() ?: return@withContext null
            val bytes = file.readBytes()
            if (bytes.size < 1 + GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) return@withContext null
            val buffer = ByteBuffer.wrap(bytes)
            val version = buffer.get()
            if (version != FORMAT_VERSION) return@withContext null
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { buffer.get(it) }
            val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(AAD_BYTES)
            val decryptedBytes = cipher.doFinal(cipherText)
            RemoteInstallStateJson.fromJson(decryptedBytes.decodeToString())
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun writeStateInternal(state: RemoteInstallState): Unit = withContext(Dispatchers.IO) {
        val key = getOrCreateSecretKey()
        val plainText = RemoteInstallStateJson.toJson(state).encodeToByteArray()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(AAD_BYTES)
        val cipherText = cipher.doFinal(plainText)

        val totalLength = 1 + GCM_IV_LENGTH_BYTES + cipherText.size
        val outputBytes = ByteBuffer.allocate(totalLength)
            .put(FORMAT_VERSION)
            .put(iv)
            .put(cipherText)
            .array()

        if (!storageDir.exists()) storageDir.mkdirs()
        val tempFile = File.createTempFile("remote_install_", ".tmp", storageDir)
        FileOutputStream(tempFile).use { it.write(outputBytes); it.flush(); it.fd.sync() }
        Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
