package dev.tabbyrush.android

import android.content.Context
import android.util.Log
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.data.AndroidAccountFiles
import dev.tabbyrush.shared.data.TabbyRushDatabase
import dev.tabbyrush.shared.data.ReminderOccurrenceStore
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.createAndroidDatabaseBuilder
import dev.tabbyrush.shared.sync.AccountLeaveProtocol
import dev.tabbyrush.shared.sync.AccountSyncCoordinator
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Factory for instantiating isolated AccountRuntime instances and their
 * constituent coordinators, delivery gates, and leave protocols.
 */
object AccountRuntimeFactory {

    private const val TAG = "AccountRuntimeFactory"

    fun create(
        context: Context,
        scope: CoroutineScope,
        accountKey: AccountKey,
        persistedGen: Long,
        activeIdentity: AccountIdentity?,
        hubConfig: HubConfig,
        accountFiles: AndroidAccountFiles,
        authSession: HubAuthSession,
        syncScheduler: AndroidSyncScheduler
    ): AccountRuntime {
        val userId = activeIdentity?.userId
            ?: authSession.currentIdentity.value?.userId
            ?: throw IllegalStateException("Cannot open runtime without established account identity for $accountKey")

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        val database = createAndroidDatabaseBuilder(context, dbFile).build()
        val store = RoomAccountStore(accountKey = accountKey, userId = userId, database = database, appId = hubConfig.appId)

        val deliveryGate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { accountFiles.readGeneration(accountKey) },
            hasPendingWipeMarker = { accountFiles.hasPendingWipe(accountKey) }
        )
        val occurrenceStore = ReminderOccurrenceStore(accountKey = accountKey, dao = database.tabbyRushDao())
        val adapter = AndroidNotificationAdapter(context)
        val reminderCoordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { accountFiles.readGeneration(accountKey) },
            hasWipeMarker = { accountFiles.hasPendingWipe(accountKey) }
        )

        val scheduleObserverJob = scope.launch {
            store.observePersistedSchedule().collect {
                try {
                    reminderCoordinator.reconcile()
                } catch (e: Exception) {
                    Log.w(TAG, "Schedule observation reconcile failed: ${e.message}")
                }
            }
        }
        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
        val surfaceObserverJob = scope.launch {
            launch {
                store.observeTodos().collect {
                    try {
                        hostOwner?.taskSurfaces?.refresh(SurfaceRefreshReason.Data)
                    } catch (e: Exception) {
                        Log.w(TAG, "Todos surface observation refresh failed: ${e.message}")
                    }
                }
            }
            launch {
                store.observePersistedSchedule().collect {
                    try {
                        hostOwner?.taskSurfaces?.refresh(SurfaceRefreshReason.Data)
                    } catch (e: Exception) {
                        Log.w(TAG, "Schedule surface observation refresh failed: ${e.message}")
                    }
                }
            }
        }

        val compositeCancelGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = deliveryGate,
            scheduleObserverJob = scheduleObserverJob,
            syncCancelGate = syncScheduler,
            reminderCancelGate = reminderCoordinator,
            surfaceObserverJob = surfaceObserverJob,
            context = context.applicationContext,
            host = hostOwner?.taskSurfaces
        )

        lateinit var leaveProto: AccountLeaveProtocol
        val coordinator = AccountSyncCoordinator(
            store = store,
            session = authSession,
            leaveProtocolProvider = { leaveProto }
        )
        coordinator.setGeneration(persistedGen)
        leaveProto = createLeaveProtocol(accountKey, store, database, authSession, accountFiles, compositeCancelGate)

        return AccountRuntime(
            accountKey = accountKey,
            generation = persistedGen,
            database = database,
            store = store,
            coordinator = coordinator,
            reminderCoordinator = reminderCoordinator,
            deliveryGate = deliveryGate,
            scheduleObserverJob = scheduleObserverJob,
            leaveProtocol = leaveProto,
            surfaceObserverJob = surfaceObserverJob
        )
    }

    private fun createLeaveProtocol(
        accountKey: AccountKey,
        store: RoomAccountStore,
        database: TabbyRushDatabase,
        authSession: HubAuthSession,
        accountFiles: AndroidAccountFiles,
        compositeCancelGate: CompositeLeaveSchedulerCancelGate
    ): AccountLeaveProtocol {
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
            schedulerCancelGate = compositeCancelGate
        )
    }
}
