package dev.doggo.shared.sync

import kotlinx.serialization.Serializable

@Serializable
data class StrictTodoDataDto(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Long
)

@Serializable
data class StrictOperationDto(
    val opId: String,
    val epoch: String,
    val tableName: String = "todos",
    val rowId: String,
    val kind: String, // "create", "update", "delete"
    val expectedRevision: Long,
    val data: StrictTodoDataDto? = null
)

@Serializable
data class StrictCurrentRecordDto(
    val revision: Long,
    val deleted: Boolean
)

@Serializable
data class StrictOperationResultDto(
    val opId: String,
    val rowId: String,
    val digest: String,
    val status: String,
    val acceptedRevision: Long? = null,
    val current: StrictCurrentRecordDto? = null
)

@Serializable
data class BootstrapResponseDto(
    val protocol: String,
    val appId: String,
    val epoch: String,
    val tables: List<String>
)

@Serializable
data class StrictPushRequestDto(
    val protocol: String,
    val epoch: String,
    val operations: List<StrictOperationDto>
)

@Serializable
data class StrictPushResponseDto(
    val protocol: String,
    val epoch: String,
    val results: List<StrictOperationResultDto>
)

@Serializable
data class StrictPullRequestDto(
    val protocol: String,
    val epoch: String,
    val tableName: String = "todos",
    val cursor: String? = null,
    val batchSize: Int = 100
)

@Serializable
data class StrictPullRecordDto(
    val tableName: String,
    val rowId: String,
    val revision: Long,
    val deleted: Boolean,
    val data: StrictTodoDataDto? = null
)

@Serializable
data class StrictPullResponseDto(
    val protocol: String,
    val epoch: String,
    val records: List<StrictPullRecordDto>,
    val cursor: String? = null,
    val hasMore: Boolean
)

@Serializable
data class StrictSyncErrorDto(
    val error: String,
    val code: String? = null
)
