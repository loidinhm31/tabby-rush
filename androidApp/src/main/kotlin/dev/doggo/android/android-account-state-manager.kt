package dev.doggo.android

import android.content.Context
import android.util.Log
import dev.doggo.shared.auth.AndroidKeystoreVault
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.data.AndroidAccountFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages active account file state and startup pending wipe recovery.
 */
object AndroidAccountStateManager {

    private const val TAG = "AndroidAccountStateManager"
    private const val FILE_ACTIVE_ACCOUNT = "active_account.dat"
    private const val FILE_ACTIVE_ACCOUNT_TMP = "active_account.tmp"

    fun readActiveAccount(filesDir: File): Pair<AccountKey, AccountIdentity>? {
        val file = File(filesDir, FILE_ACTIVE_ACCOUNT)
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            if (lines.size >= 4) {
                val key = AccountKey(lines[0])
                val identity = AccountIdentity(userId = lines[1], username = lines[2], email = lines[3])
                Pair(key, identity)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveActiveAccount(filesDir: File, identity: AccountIdentity, key: AccountKey) {
        try {
            val tmp = File(filesDir, FILE_ACTIVE_ACCOUNT_TMP)
            val dest = File(filesDir, FILE_ACTIVE_ACCOUNT)
            tmp.writeText("${key.value}\n${identity.userId}\n${identity.username}\n${identity.email}\n")
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {}
    }

    fun clearActiveAccount(filesDir: File) {
        try {
            File(filesDir, FILE_ACTIVE_ACCOUNT).delete()
        } catch (_: Exception) {}
    }

    suspend fun completePendingWipesSuspend(
        context: Context,
        accountFiles: AndroidAccountFiles,
        syncScheduler: AndroidSyncScheduler,
        vault: AndroidKeystoreVault,
        filesDir: File
    ) {
        try {
            val accountsDir = File(filesDir, "accounts")
            if (!accountsDir.exists() || !accountsDir.isDirectory) return
            val dirs = accountsDir.listFiles() ?: return
            for (dir in dirs) {
                val name = dir.name
                if (dir.isDirectory && name.length == 64 && name.all { it in '0'..'9' || it in 'a'..'f' }) {
                    val key = AccountKey(name)
                    if (accountFiles.hasPendingWipe(key)) {
                        Log.w(TAG, "Pending wipe marker detected for $key on startup. Completing fail-closed wipe.")
                        syncScheduler.cancelAndAwait(key)
                        AndroidNotificationAdapter(context).cancelAllForAccount(key)
                        if (accountFiles.deleteDatabaseAndArtifacts(key)) {
                            withContext(Dispatchers.IO) { vault.clear(key) }
                            accountFiles.clearWipeMarker(key)
                            val active = readActiveAccount(filesDir)
                            if (active != null && active.first == key) {
                                clearActiveAccount(filesDir)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in completePendingWipesSuspend: ${e.message}", e)
        }
    }

    fun completePendingWipes(
        context: Context,
        accountFiles: AndroidAccountFiles,
        syncScheduler: AndroidSyncScheduler,
        vault: AndroidKeystoreVault,
        filesDir: File
    ) {
        runBlocking(Dispatchers.Default) {
            completePendingWipesSuspend(context, accountFiles, syncScheduler, vault, filesDir)
        }
    }
}
