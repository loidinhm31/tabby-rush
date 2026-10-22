package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.tabbyrush.shared.contract.AccessLossEvidence
import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.TaskDataContract
import dev.tabbyrush.shared.contract.TaskRef

/**
 * AccessLossEvidenceEntity stores content-free, non-replayable failure evidence
 * upon authoritative collaborative access loss.
 *
 * Privacy Invariant: NEVER contains task title, body, deadline, assignee, JSON, or diff.
 */
@Entity(
    tableName = "access_loss_evidence",
    indices = [
        Index(value = ["ownerUserId", "tableName", "rowId"])
    ]
)
data class AccessLossEvidenceEntity(
    @PrimaryKey
    val evidenceId: String,
    val ownerUserId: String,
    val tableName: String = TaskDataContract.DEFAULT_TABLE_NAME,
    val rowId: String,
    val opId: String? = null,
    val baseVersion: Long? = null,
    val submittedVersion: Long? = null,
    val timestamp: Long,
    val reason: String
) {
    fun toAccessLossEvidence(): AccessLossEvidence {
        val r = try {
            AccessLossReason.valueOf(reason)
        } catch (_: Exception) {
            AccessLossReason.REVOKED
        }
        return AccessLossEvidence(
            ref = TaskRef(ownerUserId = ownerUserId, rowId = rowId),
            opId = opId,
            baseVersion = baseVersion,
            submittedVersion = submittedVersion,
            timestamp = timestamp,
            reason = r
        )
    }

    companion object {
        fun fromModel(evidenceId: String, model: AccessLossEvidence): AccessLossEvidenceEntity {
            return AccessLossEvidenceEntity(
                evidenceId = evidenceId,
                ownerUserId = model.ref.ownerUserId,
                tableName = TaskDataContract.DEFAULT_TABLE_NAME,
                rowId = model.ref.rowId,
                opId = model.opId,
                baseVersion = model.baseVersion,
                submittedVersion = model.submittedVersion,
                timestamp = model.timestamp,
                reason = model.reason.name
            )
        }
    }
}
