package dev.tabbyrush.android

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import dev.tabbyrush.shared.auth.AndroidKeystoreVault
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.data.AndroidAccountFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
                val identity = AccountIdentity(
                    userId = lines[1],
                    username = lines[2],
                    email = lines[3]
                )
                Pair(key, identity)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading active account file: ${e.message}", e)
            null
        }
    }

    fun saveActiveAccount(filesDir: File, identity: AccountIdentity, key: AccountKey) {
        val file = File(filesDir, FILE_ACTIVE_ACCOUNT)
        val tmpFile = File(filesDir, FILE_ACTIVE_ACCOUNT_TMP)
        try {
            tmpFile.writeText("${key.value}\n${identity.userId}\n${identity.username}\n${identity.email}\n")
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing active account file: ${e.message}", e)
        }
    }

    fun clearActiveAccount(filesDir: File) {
        val file = File(filesDir, FILE_ACTIVE_ACCOUNT)
        if (file.exists()) {
            file.delete()
        }
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
                        // 1. Cancel background sync
                        syncScheduler.cancelAndAwait(key)

                        // 2. Cancel reminders
                        AndroidNotificationAdapter(context).cancelAllForAccount(key)

                        // 3. Cancel task surface boundary work, notifications, widget state, preferences
                        TaskSurfaceRefreshWorker.cancelBoundaryWake(context, key)
                        TaskWidgetUpdater.clearAllInstances(context)

                        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
                        if (hostOwner != null) {
                            val preferences = TaskSurfacePreferences(context)
                            TaskSurfaceNotificationCoordinator(
                                context = context,
                                host = hostOwner.taskSurfaces,
                                preferences = preferences
                            ).cancelForAccount(key)
                        } else {
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
                        }
                        TaskSurfacePreferences(context).clear(key)

                        // 4. Delete database and credentials, clear marker only on success
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
        } catch (ce: CancellationException) {
            throw ce
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
