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

const val COLLABORATION_VERSION_1 = 1L
const val ERROR_CODE_COLLABORATION_REQUIRED = "COLLABORATION_REQUIRED"
const val ERROR_CODE_ACCESS_REVISION_CHANGED = "ACCESS_REVISION_CHANGED"
const val ERROR_CODE_ACCESS_DENIED = "ACCESS_DENIED"
const val ERROR_CODE_OPERATION_FORBIDDEN = "OPERATION_FORBIDDEN"

@Serializable
data class CanonicalRecordRefDto(
    val ownerUserId: String,
    val tableName: String,
    val rowId: String
)

@Serializable
data class TaskCapabilitiesDto(
    val editContent: Boolean,
    val manageAssignment: Boolean,
    val deleteTask: Boolean,
    val createChild: Boolean
)

@Serializable
data class TaskAccessResultDto(
    val ref: CanonicalRecordRefDto,
    val status: String,
    val capabilities: TaskCapabilitiesDto? = null,
    val hierarchyCoverage: String? = null
)

@Serializable
data class SyncCheckpointDto(val updatedAt: String, val id: String)

@Serializable
data class PushRecordDto(
    val tableName: String = "todos",
    val rowId: String,
    val data: JsonObject,
    val version: Long,
    val deleted: Boolean = false,
    val assumedServerVersion: Long,
    val ownerUserId: String? = null
)

@Serializable
data class PushRequestDto(
    val records: List<PushRecordDto>,
    val clientTimestamp: String? = null,
    val collaborationVersion: Long? = null
)

@Serializable
data class SyncedRecordDto(
    val tableName: String,
    val rowId: String,
    val version: Long,
    val ownerUserId: String? = null
)

@Serializable
data class FailedRecordDto(
    val tableName: String,
    val rowId: String,
    val error: String,
    val ownerUserId: String? = null
)

@Serializable
data class ConflictInfoDto(
    val tableName: String,
    val rowId: String,
    val clientVersion: Long,
    val serverVersion: Long,
    val ownerUserId: String? = null
)

@Serializable
data class PushResponseDto(
    val synced: Int,
    val conflicts: List<ConflictInfoDto>,
    val failures: List<FailedRecordDto> = emptyList(),
    val syncedRecords: List<SyncedRecordDto> = emptyList(),
    val serverTimestamp: String,
    val schemaVersion: Long,
    val collaborationVersion: Long? = null
)

@Serializable
data class PullRequestDto(
    val checkpoint: SyncCheckpointDto? = null,
    val batchSize: Int = 100,
    val tables: List<String>? = null,
    val collaborationVersion: Long? = null,
    val expectedAccessRevision: Long? = null,
    val accessChecks: List<CanonicalRecordRefDto>? = null
)

@Serializable
data class PullRecordDto(
    val tableName: String,
    val rowId: String,
    val data: JsonElement,
    val version: Long,
    val syncedAt: String,
    val deleted: Boolean = false,
    val ownerUserId: String? = null
)

@Serializable
data class PullResponseDto(
    val records: List<PullRecordDto>,
    val checkpoint: SyncCheckpointDto,
    val serverTimestamp: String,
    val hasMore: Boolean = false,
    val schemaVersion: Long,
    val collaborationVersion: Long? = null,
    val accessRevision: Long? = null,
    val accessResults: List<TaskAccessResultDto>? = null
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
