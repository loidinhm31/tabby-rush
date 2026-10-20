package dev.tabbyrush.shared.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

const val MAX_EXACT_VERSION = 9_007_199_254_740_991L
internal const val MAX_SYNC_BODY_BYTES = 256 * 1024
internal val syncWireJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class SyncCheckpointDto(val updatedAt: String, val id: String)

@Serializable
data class PushRecordDto(
    val tableName: String = "todos",
    val rowId: String,
    val data: JsonObject,
    val version: Long,
    val deleted: Boolean = false,
    val assumedServerVersion: Long
)

@Serializable
data class PushRequestDto(val records: List<PushRecordDto>, val clientTimestamp: String? = null)

@Serializable
data class SyncedRecordDto(val tableName: String, val rowId: String, val version: Long)

@Serializable
data class FailedRecordDto(val tableName: String, val rowId: String, val error: String)

@Serializable
data class ConflictInfoDto(
    val tableName: String,
    val rowId: String,
    val clientVersion: Long,
    val serverVersion: Long
)

@Serializable
data class PushResponseDto(
    val synced: Int,
    val conflicts: List<ConflictInfoDto>,
    val failures: List<FailedRecordDto> = emptyList(),
    val syncedRecords: List<SyncedRecordDto> = emptyList(),
    val serverTimestamp: String,
    val schemaVersion: Long
)

@Serializable
data class PullRequestDto(
    val checkpoint: SyncCheckpointDto? = null,
    val batchSize: Int = 100,
    val tables: List<String> = listOf("todos")
)

@Serializable
data class PullRecordDto(
    val tableName: String,
    val rowId: String,
    val data: JsonElement,
    val version: Long,
    val syncedAt: String,
    val deleted: Boolean = false
)

@Serializable
data class PullResponseDto(
    val records: List<PullRecordDto>,
    val checkpoint: SyncCheckpointDto,
    val serverTimestamp: String,
    val hasMore: Boolean = false,
    val schemaVersion: Long
)

@Serializable
data class DeltaRequestDto(val push: PushRequestDto? = null, val pull: PullRequestDto? = null)

@Serializable
data class DeltaResponseDto(
    val push: PushResponseDto? = null,
    val pull: PullResponseDto? = null,
    @kotlinx.serialization.Transient val pushError: String? = null,
    @kotlinx.serialization.Transient val pullError: String? = null
)
