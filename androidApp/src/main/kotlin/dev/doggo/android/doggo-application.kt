package dev.doggo.android

import android.app.Application
import android.content.Context
import android.os.UserManager
import android.util.Log
import dev.doggo.shared.auth.AndroidKeystoreVault
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.data.AndroidAccountFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DoggoApplication : Application(), TaskSurfaceHostOwner {

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

    val taskSurfaceRuntimeCoordinator by lazy { TaskSurfaceRuntimeCoordinator(this) }
    override val taskSurfaces: AndroidTaskSurfaceHost
        get() = taskSurfaceRuntimeCoordinator

    private val runtimeMutex = Mutex()
    private var activeRuntime: AccountRuntime? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        completePendingWipesOnStartup()

        val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            applicationScope.launch {
                taskSurfaceRuntimeCoordinator.setLocked()
            }
            return
        }

        val active = readActiveAccountSync()
        if (active == null) {
            applicationScope.launch {
                taskSurfaceRuntimeCoordinator.setSignedOut()
                try {
                    TaskWidgetUpdater.clearAll(this@DoggoApplication)
                } catch (_: Exception) {}
            }
            return
        }

        applicationScope.launch { checkAndRestoreEstablishedSession() }
    }

    fun completePendingWipesOnStartup() {
        AndroidAccountStateManager.completePendingWipes(this, accountFiles, syncScheduler, vault, filesDir)
    }

    suspend fun completePendingWipesSuspend() {
        AndroidAccountStateManager.completePendingWipesSuspend(this, accountFiles, syncScheduler, vault, filesDir)
    }

    fun readActiveAccountSync(): Pair<AccountKey, AccountIdentity>? =
        AndroidAccountStateManager.readActiveAccount(filesDir)

    fun saveActiveAccount(identity: AccountIdentity, key: AccountKey) {
        AndroidAccountStateManager.saveActiveAccount(filesDir, identity, key)
    }

    fun clearActiveAccount() {
        AndroidAccountStateManager.clearActiveAccount(filesDir)
        applicationScope.launch {
            taskSurfaceRuntimeCoordinator.setSignedOut()
            try {
                TaskWidgetUpdater.clearAll(this@DoggoApplication)
            } catch (_: Exception) {}
        }
    }

    private suspend fun checkAndRestoreEstablishedSession() {
        if (authSession.state.value != AuthState.UNAUTHENTICATED) return
        val active = readActiveAccountSync()
        if (active == null) {
            taskSurfaceRuntimeCoordinator.setSignedOut()
            try {
                TaskWidgetUpdater.clearAll(this)
            } catch (_: Exception) {}
            return
        }

        val key = active.first
        val identity = active.second
        if (accountFiles.hasPendingWipe(key)) {
            taskSurfaceRuntimeCoordinator.setUnavailable()
            return
        }

        try {
            if (authSession.restoreSession(identity, key)) {
                getOrCreateAccountRuntime(key)
            } else {
                taskSurfaceRuntimeCoordinator.setUnavailable()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore active account: ${e.message}")
            taskSurfaceRuntimeCoordinator.setUnavailable()
        }
    }

    suspend fun getOrCreateAccountRuntime(accountKey: AccountKey): AccountRuntime = runtimeMutex.withLock {
        val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            taskSurfaceRuntimeCoordinator.setLocked()
            throw IllegalStateException("Cannot open account runtime: device storage is locked")
        }

        if (accountFiles.hasPendingWipe(accountKey)) {
            taskSurfaceRuntimeCoordinator.setUnavailable()
            throw IllegalStateException("Cannot open account runtime: pending wipe marker exists for $accountKey")
        }

        val persistedGen = accountFiles.readGeneration(accountKey)
        val existing = activeRuntime
        if (existing != null && existing.accountKey == accountKey && existing.generation == persistedGen) {
            taskSurfaceRuntimeCoordinator.setReady(SurfaceAccountToken(accountKey, persistedGen))
            return existing
        }

        if (existing != null) closeRuntimeInternal(existing)

        val active = readActiveAccountSync()
        val activeIdentity = if (active != null && active.first == accountKey) active.second else null

        val runtime = AccountRuntimeFactory.create(
            context = this,
            scope = applicationScope,
            accountKey = accountKey,
            persistedGen = persistedGen,
            activeIdentity = activeIdentity,
            hubConfig = hubConfig,
            accountFiles = accountFiles,
            authSession = authSession,
            syncScheduler = syncScheduler
        )
        activeRuntime = runtime
        val token = SurfaceAccountToken(accountKey, persistedGen)
        taskSurfaceRuntimeCoordinator.setReady(token)
        taskSurfaceRuntimeCoordinator.refresh(SurfaceRefreshReason.Account)
        runtime
    }

    fun getActiveRuntime(): AccountRuntime? = activeRuntime

    suspend fun closeActiveRuntime() = runtimeMutex.withLock {
        activeRuntime?.let { closeRuntimeInternal(it) }
        activeRuntime = null
    }

    private suspend fun closeRuntimeInternal(runtime: AccountRuntime) {
        try {
            taskSurfaceRuntimeCoordinator.transitionToNonReady(SurfaceReadiness.Restoring)
            val drained = runtime.deliveryGate.closeAndDrain()
            if (!drained) {
                throw IllegalStateException("Delivery gate drain timed out for ${runtime.accountKey}; failing closed")
            }
            runtime.surfaceObserverJob?.cancelAndJoin()
            runtime.scheduleObserverJob.cancelAndJoin()
            runtime.store.isFrozen = true
            runtime.database.close()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error closing database for ${runtime.accountKey}: ${e.message}", e)
        }
    }
}
