package dev.doggo.android

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.data.DoggoDatabase
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.sync.AccountLeaveProtocol
import dev.doggo.shared.sync.AccountSyncCoordinator
import dev.doggo.shared.sync.ReminderDeliveryGate
import dev.doggo.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.Job

/**
 * Isolated account runtime encapsulating local Room SQLite persistence,
 * strict sync coordination, reactive schedule observation, task surface observation,
 * and native reminder scheduling.
 */
class AccountRuntime(
    val accountKey: AccountKey,
    val generation: Long,
    val database: DoggoDatabase,
    val store: RoomAccountStore,
    val coordinator: AccountSyncCoordinator,
    val reminderCoordinator: ReminderSchedulingCoordinator,
    val deliveryGate: ReminderDeliveryGate,
    val scheduleObserverJob: Job,
    val leaveProtocol: AccountLeaveProtocol,
    val surfaceObserverJob: Job? = null
)
