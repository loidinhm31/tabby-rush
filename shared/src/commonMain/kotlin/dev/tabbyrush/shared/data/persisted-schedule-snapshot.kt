package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.PersistedAccessRecord
import dev.tabbyrush.shared.contract.PersistedBaseRecord
import dev.tabbyrush.shared.contract.PersistedOutboxRecord
import dev.tabbyrush.shared.contract.PersistedProjectionInputs

/**
 * PersistedScheduleSnapshot holds a consistent atomic snapshot of server_base, outbox, and access records.
 */
data class PersistedScheduleSnapshot(
    val serverBases: List<PersistedBaseRecord>,
    val outboxEntries: List<PersistedOutboxRecord>,
    val accessRecords: List<PersistedAccessRecord> = emptyList()
) {
    fun toProjectionInputs(): PersistedProjectionInputs = PersistedProjectionInputs(
        serverBases = serverBases,
        outboxEntries = outboxEntries,
        accessRecords = accessRecords
    )
}
