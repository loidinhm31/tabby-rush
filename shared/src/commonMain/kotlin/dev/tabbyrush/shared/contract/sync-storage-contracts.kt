package dev.tabbyrush.shared.contract

import kotlinx.serialization.Serializable

/**
 * An outbox mutation frozen for dispatch over network.
 */
@Serializable
data class FrozenOutboxEntry(
    val opId: String,
    val ref: CanonicalRecordRef,
    val kind: TaskOperationKind,
    val dataJson: String,
    val version: Long,
    val assumedServerVersion: Long?,
    val deleted: Boolean,
    val intentMask: String?
)

/**
 * Tuple identifying an accepted record mutation acknowledged by the server.
 * Requires exact (ref, submittedVersion) match against the frozen local intent.
 */
@Serializable
data class AcknowledgedTuple(
    val ref: CanonicalRecordRef,
    val submittedVersion: Long
)

/**
 * Tuple identifying a conflict detected by the server.
 */
@Serializable
data class ConflictTuple(
    val ref: CanonicalRecordRef,
    val clientVersion: Long,
    val serverVersion: Long
)

/**
 * Failure result for an individual pushed record.
 */
@Serializable
data class RecordFailure(
    val ref: CanonicalRecordRef,
    val error: String
)

/**
 * Abstraction for local persistent sync storage without Room or SQLite dependencies.
 */
interface SyncStorage {
    /**
     * Freeze eligible pending outbox entries under mutation lock for network dispatch.
     */
    suspend fun prepareFrozenOutbox(): List<FrozenOutboxEntry>

    /**
     * Clear acknowledged mutations matching exact (ref, submittedVersion).
     */
    suspend fun acknowledgeAccepted(tuples: List<AcknowledgedTuple>)

    /**
     * Record conflict states for rejected mutations.
     */
    suspend fun recordConflicts(conflicts: List<ConflictTuple>)

    /**
     * Record persistence failures for rejected mutations.
     */
    suspend fun recordFailures(failures: List<RecordFailure>)

    /**
     * Read the confirmed server base snapshot for a canonical record reference.
     */
    suspend fun readConfirmedBase(ref: CanonicalRecordRef): CanonicalRecordSnapshot?
}
