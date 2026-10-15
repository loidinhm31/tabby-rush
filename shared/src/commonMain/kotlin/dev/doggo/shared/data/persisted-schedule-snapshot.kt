package dev.doggo.shared.data

/**
 * PersistedScheduleSnapshot holds a consistent atomic snapshot of server_base and outbox.
 */
data class PersistedScheduleSnapshot(
    val serverBases: List<ServerBaseEntity>,
    val outboxEntries: List<OutboxEntity>
)
