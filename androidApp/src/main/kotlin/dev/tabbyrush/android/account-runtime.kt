package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.RemoteInboxController
import dev.tabbyrush.shared.data.TabbyRushDatabase
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.sync.AccountLeaveProtocol
import dev.tabbyrush.shared.sync.AccountSyncCoordinator
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.Job

/**
 * Isolated account runtime encapsulating local Room SQLite persistence,
 * strict sync coordination, reactive schedule observation, task surface observation,
 * and native reminder scheduling.
 */
class AccountRuntime(
    val accountKey: AccountKey,
    val generation: Long,
    val database: TabbyRushDatabase,
    val store: RoomAccountStore,
    val coordinator: AccountSyncCoordinator,
    val reminderCoordinator: ReminderSchedulingCoordinator,
    val deliveryGate: ReminderDeliveryGate,
    val scheduleObserverJob: Job,
    val leaveProtocol: AccountLeaveProtocol,
    val surfaceObserverJob: Job? = null,
    val remoteController: RemoteInboxController? = null,
    val installCoordinator: RemoteInstallCoordinator? = null
)
