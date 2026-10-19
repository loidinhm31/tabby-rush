package dev.tabbyrush.shared.sync

import kotlinx.serialization.Serializable

@Serializable
data class StrictTodoDataDto(
    val id: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class StrictV2TodoDataDto(
    val id: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val createdAt: Long = 0L,
    val deadlineDate: String? = null,
    val deadlineTime: String? = null,
    val deadlineZone: String? = null,
    val deadlineOffsetSeconds: Long? = null,
    val reminderAnchorTime: String? = null,
    val deadlineAnchorEpochMs: Long? = null,
    val deadlineOverdueEpochMs: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Long = 0L,
    val reminderIntervalMinutes: Long? = null,
    val reminderCount: Long = 1L,
    val scheduleToken: String? = null
)

@Serializable
data class StrictV2OperationDto(
    val opId: String,
    val epoch: String,
    val tableName: String = "todos",
    val rowId: String,
    val kind: String, // "create", "update", "delete"
    val expectedRevision: Long,
    val data: StrictV2TodoDataDto? = null
)

@Serializable
data class StrictV2PushRequestDto(
    val protocol: String = "strict-v2",
    val epoch: String,
    val operations: List<StrictV2OperationDto>
)

@Serializable
data class StrictV3TodoDataDto(
    val id: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val createdAt: Long = 0L,
    val deadlineDate: String? = null,
    val deadlineTime: String? = null,
    val deadlineZone: String? = null,
    val deadlineOffsetSeconds: Long? = null,
    val reminderAnchorTime: String? = null,
    val deadlineAnchorEpochMs: Long? = null,
    val deadlineOverdueEpochMs: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Long = 0L,
    val reminderIntervalMinutes: Long? = null,
    val reminderCount: Long = 1L,
    val scheduleToken: String? = null,
    val parentTaskId: String? = null
)

@Serializable
data class StrictV3OperationDto(
    val opId: String,
    val epoch: String,
    val tableName: String = "todos",
    val rowId: String,
    val kind: String, // "create", "update", "delete"
    val expectedRevision: Long,
    val data: StrictV3TodoDataDto? = null
)

@Serializable
data class StrictV3PushRequestDto(
    val protocol: String = "strict-v3",
    val epoch: String,
    val operations: List<StrictV3OperationDto>
)

object LegacyReceiptStatus {
    const val PROVEN_ACCEPTED = "provenAccepted"
    const val PROVEN_NOT_ACCEPTED = "provenNotAccepted"
    const val UNKNOWN_SUPERSEDED = "unknownSuperseded"
    const val INTEGRITY_FAILURE = "integrityFailure"
}

@Serializable
data class LegacyReceiptItemDto(
    val opId: String,
    val tableName: String = "todos",
    val rowId: String,
    val kind: String,
    val expectedRevision: Long,
    val digest: String,
    val data: StrictTodoDataDto? = null
)

@Serializable
data class LegacyReceiptsRequestDto(
    val protocol: String = "strict-v1",
    val epoch: String,
    val receipts: List<LegacyReceiptItemDto>
)

@Serializable
data class LegacyReceiptResultDto(
    val opId: String,
    val rowId: String,
    val status: String,
    val acceptedRevision: Long? = null,
    val current: StrictCurrentRecordDto? = null
)

@Serializable
data class LegacyReceiptsResponseDto(
    val protocol: String,
    val epoch: String,
    val results: List<LegacyReceiptResultDto>
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
    val data: StrictV3TodoDataDto? = null
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
