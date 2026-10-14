package dev.doggo.android

import android.app.Application
import android.util.Log
import dev.doggo.shared.auth.AndroidKeystoreVault
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.data.AndroidAccountFiles
import dev.doggo.shared.data.DoggoDatabase
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.createAndroidDatabaseBuilder
import dev.doggo.shared.sync.AccountLeaveProtocol
import dev.doggo.shared.sync.AccountSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AccountRuntime(
    val accountKey: AccountKey,
    val generation: Long,
    val database: DoggoDatabase,
    val store: RoomAccountStore,
    val coordinator: AccountSyncCoordinator,
    val leaveProtocol: AccountLeaveProtocol
)

class DoggoApplication : Application() {

    companion object {
        private const val TAG = "DoggoApplication"
        lateinit var instance: DoggoApplication
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val hubConfig by lazy {
        val origin = if (BuildConfig.DEBUG) BuildConfig.HUB_ORIGIN_DEBUG else BuildConfig.HUB_ORIGIN_RELEASE
        val mode = if (BuildConfig.DEBUG) BuildMode.DEBUG else BuildMode.RELEASE
        HubConfig(canonicalOrigin = origin, appId = "doggo", appApiKey = BuildConfig.HUB_API_KEY, buildMode = mode)
    }

    val vault by lazy { AndroidKeystoreVault(this) }
    val accountFiles by lazy { AndroidAccountFiles(this) }
    val syncScheduler by lazy { AndroidSyncScheduler(this) }
    val authSession by lazy { HubAuthSession(config = hubConfig, vault = vault) }

    private val runtimeMutex = Mutex()
    private var activeRuntime: AccountRuntime? = null
    private val activeAccountFile by lazy { File(filesDir, "active_account.dat") }

    override fun onCreate() {
        super.onCreate()
        instance = this
        completePendingWipesOnStartup()
        applicationScope.launch { checkAndRestoreEstablishedSession() }
    }

    fun completePendingWipesOnStartup() {
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
                        syncScheduler.cancel(key)
                        if (accountFiles.deleteDatabaseAndArtifacts(key)) {
                            runBlocking(Dispatchers.IO) { vault.clear(key) }
                            accountFiles.clearWipeMarker(key)
                            if (activeAccountFile.exists() && activeAccountFile.readLines().firstOrNull() == key.value) {
                                clearActiveAccount()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in completePendingWipesOnStartup: ${e.message}", e)
        }
    }

    fun saveActiveAccount(identity: AccountIdentity, key: AccountKey) {
        try {
            val tmp = File(filesDir, "active_account.tmp")
            tmp.writeText("${key.value}\n${identity.userId}\n${identity.username}\n${identity.email}\n")
            Files.move(tmp.toPath(), activeAccountFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {}
    }

    fun clearActiveAccount() {
        try { activeAccountFile.delete() } catch (_: Exception) {}
    }

    private suspend fun checkAndRestoreEstablishedSession() {
        if (authSession.state.value != AuthState.UNAUTHENTICATED) return
        if (!activeAccountFile.exists()) return
        try {
            val lines = activeAccountFile.readLines()
            if (lines.size >= 4) {
                val key = AccountKey(lines[0])
                val identity = AccountIdentity(userId = lines[1], username = lines[2], email = lines[3])
                if (accountFiles.hasPendingWipe(key)) return
                if (authSession.restoreSession(identity, key)) {
                    getOrCreateAccountRuntime(key)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore active account: ${e.message}")
        }
    }

    suspend fun getOrCreateAccountRuntime(accountKey: AccountKey): AccountRuntime = runtimeMutex.withLock {
        if (accountFiles.hasPendingWipe(accountKey)) {
            throw IllegalStateException("Cannot open account runtime: pending wipe marker exists for $accountKey")
        }

        val persistedGen = accountFiles.readGeneration(accountKey)
        val existing = activeRuntime
        if (existing != null && existing.accountKey == accountKey && existing.generation == persistedGen) {
            return existing
        }

        if (existing != null) closeRuntimeInternal(existing)

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        val database = createAndroidDatabaseBuilder(this, dbFile).build()
        val userId = authSession.currentIdentity.value?.userId ?: "default"
        val store = RoomAccountStore(accountKey = accountKey, userId = userId, database = database, appId = hubConfig.appId)

        lateinit var leaveProto: AccountLeaveProtocol
        val coordinator = AccountSyncCoordinator(
            store = store,
            session = authSession,
            leaveProtocolProvider = { leaveProto }
        )
        coordinator.setGeneration(persistedGen)
        leaveProto = createLeaveProtocol(accountKey, store, database)

        val runtime = AccountRuntime(accountKey, persistedGen, database, store, coordinator, leaveProto)
        activeRuntime = runtime
        runtime
    }

    fun getActiveRuntime(): AccountRuntime? = activeRuntime

    suspend fun closeActiveRuntime() = runtimeMutex.withLock {
        activeRuntime?.let { closeRuntimeInternal(it) }
        activeRuntime = null
    }

    private fun closeRuntimeInternal(runtime: AccountRuntime) {
        try {
            runtime.store.isFrozen = true
            runtime.database.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing database for ${runtime.accountKey}: ${e.message}", e)
        }
    }

    private fun createLeaveProtocol(accountKey: AccountKey, store: RoomAccountStore, database: DoggoDatabase): AccountLeaveProtocol {
        return AccountLeaveProtocol(
            store = store,
            session = authSession,
            onReadGeneration = { accountFiles.readGeneration(it) },
            onWriteGeneration = { key, gen -> accountFiles.writeGeneration(key, gen) },
            onWriteWipeMarker = { key, gen -> accountFiles.writeWipeMarker(key, gen) },
            onClearWipeMarker = { key -> accountFiles.clearWipeMarker(key) },
            onDeleteDatabaseArtifacts = { key ->
                try { database.close() } catch (e: Exception) { Log.e(TAG, "Error closing database: ${e.message}", e) }
                accountFiles.deleteDatabaseAndArtifacts(key)
            },
            schedulerCancelGate = syncScheduler
        )
    }
}
