package dev.tabbyrush.android

import android.app.Application
import android.content.Context
import android.os.UserManager
import android.util.Log
import dev.tabbyrush.shared.auth.AndroidKeystoreVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.data.AndroidAccountFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TabbyRushApplication : Application(), TaskSurfaceHostOwner {

    companion object {
        private const val TAG = "TabbyRushApplication"
        lateinit var instance: TabbyRushApplication
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val hubConfig by lazy {
        val origin = if (BuildConfig.DEBUG) BuildConfig.HUB_ORIGIN_DEBUG else BuildConfig.HUB_ORIGIN_RELEASE
        val mode = if (BuildConfig.DEBUG) BuildMode.DEBUG else BuildMode.RELEASE
        HubConfig(canonicalOrigin = origin, appId = "tabby-rush", appApiKey = BuildConfig.HUB_API_KEY, buildMode = mode)
    }

    val vault by lazy { AndroidKeystoreVault(this) }
    val accountFiles by lazy { AndroidAccountFiles(this) }
    val syncScheduler by lazy { AndroidSyncScheduler(this) }
    val authSession by lazy { HubAuthSession(config = hubConfig, vault = vault) }
    val taskSurfaceRuntimeCoordinator by lazy { TaskSurfaceRuntimeCoordinator(this) }
    override val taskSurfaces: AndroidTaskSurfaceHost
        get() = taskSurfaceRuntimeCoordinator

    val remoteNotificationAdapter by lazy { AndroidRemoteNotificationAdapter(this) }
    val installStore: RemoteInstallStore by lazy { EncryptedRemoteInstallStore(this) }
    val remoteHttpClient by lazy { HubHttpClient(hubConfig) }
    val remoteInboxClient by lazy { dev.tabbyrush.shared.remote.RemoteInboxClient(remoteHttpClient, authSession) }
    val installCoordinator by lazy {
        RemoteInstallCoordinator(
            store = installStore,
            clientProvider = { remoteInboxClient },
            authSession = authSession,
            appVersion = BuildConfig.VERSION_NAME,
            scope = applicationScope
        )
    }
    private val runtimeMutex = Mutex()
    private var activeRuntime: AccountRuntime? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        completePendingWipesOnStartup()

        if (BuildConfig.FIREBASE_CONFIGURED) {
            try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setGcmSenderId(BuildConfig.FIREBASE_GCM_SENDER_ID)
                    .build()
                if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                    com.google.firebase.FirebaseApp.initializeApp(this, options)
                }
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            if (!token.isNullOrBlank()) {
                                applicationScope.launch {
                                    installCoordinator.onNewToken(token)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize FirebaseApp: ${e.message}")
            }
        }

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
                    TaskWidgetUpdater.clearAll(this@TabbyRushApplication)
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
                TaskWidgetUpdater.clearAll(this@TabbyRushApplication)
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
        try {
            val localSettings = runtime.store.getLocalSettings()
            if (localSettings.notificationsEnabled) {
                val surfacePrefs = TaskSurfacePreferences(this@TabbyRushApplication)
                surfacePrefs.setEnabled(token, true)
            }
        } catch (_: Exception) {}
        taskSurfaceRuntimeCoordinator.refresh(SurfaceRefreshReason.Account)
        runtime
    }

    fun getActiveRuntime(): AccountRuntime? = activeRuntime

    suspend fun closeActiveRuntime() = runtimeMutex.withLock {
        val existing = activeRuntime ?: return@withLock
        closeRuntimeInternal(existing)
        activeRuntime = null
    }

    private suspend fun closeRuntimeInternal(runtime: AccountRuntime) {
        taskSurfaceRuntimeCoordinator.transitionToNonReady(SurfaceReadiness.Restoring)
        val drained = runtime.deliveryGate.closeAndDrain()
        if (!drained) {
            throw IllegalStateException("Delivery gate drain timed out for ${runtime.accountKey}; failing closed")
        }
        runtime.remoteController?.let { controller ->
            val remoteQuiesce = controller.quiesce()
            if (remoteQuiesce is dev.tabbyrush.shared.contract.RemoteQuiesceResult.TimedOut) {
                throw IllegalStateException("Remote controller quiesce timed out for ${runtime.accountKey}; failing closed")
            }
            controller.close()
        }
        runtime.surfaceObserverJob?.cancelAndJoin()
        runtime.scheduleObserverJob.cancelAndJoin()
        runtime.store.isFrozen = true
        runtime.database.close()
    }
}
