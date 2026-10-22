package dev.tabbyrush.shared.contract

/**
 * Delivery state constants for persisted outbox mutations.
 */
object OutboxDeliveryStates {
    const val PENDING = "PENDING"
    const val DISPATCHED = "DISPATCHED"
    const val UNCERTAIN = "UNCERTAIN"
    const val CONFLICT = "CONFLICT"
    const val REVIEW = "REVIEW"
}

/**
 * Intent mask field keys for fine-grained updates in outbox mutations.
 */
object OutboxIntentMasks {
    const val TITLE = "TITLE"
    const val COMPLETED = "COMPLETED"
    const val DEADLINE = "DEADLINE"
    const val REMINDER = "REMINDER"
    const val PARENT = "PARENT"
    const val DELETE = "DELETE"
    const val LAST_CHILD_PRESERVATION = "LAST_CHILD_PRESERVATION"
    const val REQUIRES_REVIEW = "REQUIRES_REVIEW"
}

/**
 * Read-only contract snapshot of a server_base record for schedule projection.
 * Completely decoupled from Room/SQLite entities.
 */
interface PersistedBaseRecord {
    val ownerUserId: String
    val tableName: String
    val rowId: String
    val title: String?
    val completed: Boolean?
    val deleted: Boolean
    val parentTaskId: String?
    val deadlineDate: String?
    val deadlineTime: String?
    val deadlineZone: String?
    val deadlineOffsetSeconds: Long?
    val reminderAnchorTime: String?
    val deadlineAnchorEpochMs: Long?
    val deadlineOverdueEpochMs: Long?
    val reminderEnabled: Boolean
    val reminderLeadMinutes: Long
    val reminderIntervalMinutes: Long?
    val reminderCount: Long
    val scheduleToken: String?
    val projectionError: String?

    val ref: CanonicalRecordRef
        get() = CanonicalRecordRef(ownerUserId = ownerUserId, tableName = tableName, rowId = rowId)

    val taskRef: TaskRef
        get() = TaskRef(ownerUserId = ownerUserId, rowId = rowId)
}

/**
 * Read-only contract snapshot of an outbox mutation for schedule projection.
 */
interface PersistedOutboxRecord {
    val ownerUserId: String
    val tableName: String
    val rowId: String
    val localRevision: Long
    val deliveryState: String
    val kind: String
    val intentMask: String?
    val parentTaskId: String?
    val title: String?
    val completed: Boolean?
    val deadlineDate: String?
    val deadlineTime: String?
    val deadlineZone: String?
    val deadlineOffsetSeconds: Long?
    val reminderAnchorTime: String?
    val deadlineAnchorEpochMs: Long?
    val deadlineOverdueEpochMs: Long?
    val reminderEnabled: Boolean
    val reminderLeadMinutes: Long
    val reminderIntervalMinutes: Long?
    val reminderCount: Long
    val scheduleToken: String?

    val ref: CanonicalRecordRef
        get() = CanonicalRecordRef(ownerUserId = ownerUserId, tableName = tableName, rowId = rowId)

    val taskRef: TaskRef
        get() = TaskRef(ownerUserId = ownerUserId, rowId = rowId)
}

/**
 * Read-only contract snapshot of an access state record for schedule projection.
 */
interface PersistedAccessRecord {
    val ownerUserId: String
    val tableName: String
    val rowId: String
    val status: TaskAccessStatus
    val capabilities: TaskCapabilities
    val coverage: HierarchyCoverage

    val ref: CanonicalRecordRef
        get() = CanonicalRecordRef(ownerUserId = ownerUserId, tableName = tableName, rowId = rowId)

    val taskRef: TaskRef
        get() = TaskRef(ownerUserId = ownerUserId, rowId = rowId)
}

/**
 * Pure, immutable inputs to [PersistedScheduleProjector].
 */
data class PersistedProjectionInputs(
    val serverBases: List<PersistedBaseRecord>,
    val outboxEntries: List<PersistedOutboxRecord>,
    val accessRecords: List<PersistedAccessRecord> = emptyList()
)

/**
 * Concrete immutable implementations of projection record contracts for testing and adapters.
 */
data class SimplePersistedBaseRecord(
    override val ownerUserId: String,
    override val tableName: String = TaskDataContract.DEFAULT_TABLE_NAME,
    override val rowId: String,
    override val title: String? = null,
    override val completed: Boolean? = false,
    override val deleted: Boolean = false,
    override val parentTaskId: String? = null,
    override val deadlineDate: String? = null,
    override val deadlineTime: String? = null,
    override val deadlineZone: String? = null,
    override val deadlineOffsetSeconds: Long? = null,
    override val reminderAnchorTime: String? = null,
    override val deadlineAnchorEpochMs: Long? = null,
    override val deadlineOverdueEpochMs: Long? = null,
    override val reminderEnabled: Boolean = false,
    override val reminderLeadMinutes: Long = 0L,
    override val reminderIntervalMinutes: Long? = null,
    override val reminderCount: Long = 1L,
    override val scheduleToken: String? = null,
    override val projectionError: String? = null
) : PersistedBaseRecord

data class SimplePersistedOutboxRecord(
    override val ownerUserId: String,
    override val tableName: String = TaskDataContract.DEFAULT_TABLE_NAME,
    override val rowId: String,
    override val localRevision: Long = 1L,
    override val deliveryState: String = OutboxDeliveryStates.PENDING,
    override val kind: String = TaskOperationKind.UPDATE.wireName,
    override val intentMask: String? = null,
    override val parentTaskId: String? = null,
    override val title: String? = null,
    override val completed: Boolean? = null,
    override val deadlineDate: String? = null,
    override val deadlineTime: String? = null,
    override val deadlineZone: String? = null,
    override val deadlineOffsetSeconds: Long? = null,
    override val reminderAnchorTime: String? = null,
    override val deadlineAnchorEpochMs: Long? = null,
    override val deadlineOverdueEpochMs: Long? = null,
    override val reminderEnabled: Boolean = false,
    override val reminderLeadMinutes: Long = 0L,
    override val reminderIntervalMinutes: Long? = null,
    override val reminderCount: Long = 1L,
    override val scheduleToken: String? = null
) : PersistedOutboxRecord

data class SimplePersistedAccessRecord(
    override val ownerUserId: String,
    override val tableName: String = TaskDataContract.DEFAULT_TABLE_NAME,
    override val rowId: String,
    override val status: TaskAccessStatus = TaskAccessStatus.Accessible,
    override val capabilities: TaskCapabilities = TaskCapabilities.OWNER,
    override val coverage: HierarchyCoverage = HierarchyCoverage.FullGroup
) : PersistedAccessRecord
