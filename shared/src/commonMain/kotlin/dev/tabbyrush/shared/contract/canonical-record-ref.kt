package dev.tabbyrush.shared.contract

import kotlinx.serialization.Serializable

/**
 * Canonical logical identity of a synchronized record across account partitions and tables:
 * (ownerUserId, tableName, rowId).
 *
 * Invariant: ownerUserId, tableName, and rowId must be non-blank.
 */
@Serializable
data class CanonicalRecordRef(
    val ownerUserId: String,
    val tableName: String,
    val rowId: String
) : Comparable<CanonicalRecordRef> {
    init {
        require(ownerUserId.isNotBlank()) { "ownerUserId must not be blank" }
        require(tableName.isNotBlank()) { "tableName must not be blank" }
        require(rowId.isNotBlank()) { "rowId must not be blank" }
    }

    override fun compareTo(other: CanonicalRecordRef): Int {
        val ownerCmp = ownerUserId.compareTo(other.ownerUserId)
        if (ownerCmp != 0) return ownerCmp
        val tableCmp = tableName.compareTo(other.tableName)
        if (tableCmp != 0) return tableCmp
        return rowId.compareTo(other.rowId)
    }

    fun toTaskRef(): TaskRef {
        require(tableName == TaskDataContract.DEFAULT_TABLE_NAME) {
            "Cannot convert table '$tableName' to TaskRef; expected '${TaskDataContract.DEFAULT_TABLE_NAME}'"
        }
        return TaskRef(ownerUserId = ownerUserId, rowId = rowId)
    }
}

/**
 * Canonical logical identity of a task: (ownerUserId, rowId).
 *
 * All public task identities, actions, surfaces, navigation, conflict reviews,
 * and reminder schedulers use TaskRef instead of bare rowId.
 */
@Serializable
data class TaskRef(
    val ownerUserId: String,
    val rowId: String
) : Comparable<TaskRef> {
    init {
        require(ownerUserId.isNotBlank()) { "ownerUserId must not be blank" }
        require(rowId.isNotBlank()) { "rowId must not be blank" }
    }

    fun toRecordRef(tableName: String = TaskDataContract.DEFAULT_TABLE_NAME): CanonicalRecordRef =
        CanonicalRecordRef(ownerUserId = ownerUserId, tableName = tableName, rowId = rowId)

    override fun compareTo(other: TaskRef): Int {
        val ownerCmp = ownerUserId.compareTo(other.ownerUserId)
        if (ownerCmp != 0) return ownerCmp
        return rowId.compareTo(other.rowId)
    }
}
