package dev.tabbyrush.shared.data

import androidx.room.Entity
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskDataContract
import dev.tabbyrush.shared.contract.TaskRef

/**
 * AccessCheckStagingEntity stores pending access check results accumulated during
 * paginated scan validation until atomic promotion.
 */
@Entity(
    tableName = "access_check_staging",
    primaryKeys = ["scanGeneration", "ownerUserId", "tableName", "rowId"]
)
data class AccessCheckStagingEntity(
    val scanGeneration: Long,
    val ownerUserId: String,
    val tableName: String = TaskDataContract.DEFAULT_TABLE_NAME,
    val rowId: String,
    val revision: Long,
    val status: String = TaskAccessStatus.Accessible.name,
    val editContent: Boolean = true,
    val manageAssignment: Boolean = false,
    val deleteTask: Boolean = false,
    val createChild: Boolean = false,
    val hierarchyCoverage: String = HierarchyCoverage.FullGroup.name
) {
    fun toTaskAccessState(): TaskAccessState {
        val st = if (status == TaskAccessStatus.Unavailable.name) {
            TaskAccessStatus.Unavailable
        } else {
            TaskAccessStatus.Accessible
        }
        val cov = try {
            HierarchyCoverage.valueOf(hierarchyCoverage)
        } catch (_: Exception) {
            HierarchyCoverage.FullGroup
        }
        return TaskAccessState(
            ref = TaskRef(ownerUserId = ownerUserId, rowId = rowId),
            revision = revision,
            capabilities = TaskCapabilities(
                editContent = editContent,
                manageAssignment = manageAssignment,
                deleteTask = deleteTask,
                createChild = createChild
            ),
            coverage = cov,
            status = st
        )
    }

    companion object {
        fun fromTaskAccessState(scanGeneration: Long, state: TaskAccessState): AccessCheckStagingEntity {
            return AccessCheckStagingEntity(
                scanGeneration = scanGeneration,
                ownerUserId = state.ref.ownerUserId,
                tableName = TaskDataContract.DEFAULT_TABLE_NAME,
                rowId = state.ref.rowId,
                revision = state.revision,
                status = state.status.name,
                editContent = state.capabilities.editContent,
                manageAssignment = state.capabilities.manageAssignment,
                deleteTask = state.capabilities.deleteTask,
                createChild = state.capabilities.createChild,
                hierarchyCoverage = state.coverage.name
            )
        }
    }
}
