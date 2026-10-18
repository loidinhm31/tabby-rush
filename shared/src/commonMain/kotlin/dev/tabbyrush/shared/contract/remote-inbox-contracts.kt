package dev.tabbyrush.shared.contract

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class RemoteInboxAvailability {
    SignedOut,
    Loading,
    Online,
    Offline,
    Revoked
}

@Serializable
data class RemoteInboxItem(
    val id: String,
    val appId: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val expiresAt: String,
    val readAt: String? = null
)

@Serializable
data class RemoteInboxPage(
    val items: List<RemoteInboxItem>,
    val nextCursor: String? = null,
    val serverNow: String
)

@Serializable
data class RemoteUnreadResponse(
    val unreadCount: Int,
    val serverNow: String
)

@Serializable
data class RemoteReadResult(
    val id: String,
    val readAt: String,
    val unreadCount: Int,
    val serverNow: String
)

@Serializable
data class RemoteBindingResult(
    val installId: String,
    val operationGeneration: String,
    val bindingGeneration: String,
    val state: String,
    val replayed: Boolean
)

@Serializable
data class RemoteBindingStatus(
    val installId: String,
    val currentGeneration: String,
    val state: String,
    val ownedByCurrentUser: Boolean,
    val lastOperationMatchesCurrentUser: Boolean
)

@Serializable
data class RemoteErrorDetail(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val requestId: String? = null,
    val details: kotlinx.serialization.json.JsonObject? = null
)

@Serializable
data class RemoteErrorEnvelope(
    val error: RemoteErrorDetail
)

data class RemoteInboxState(
    val availability: RemoteInboxAvailability = RemoteInboxAvailability.SignedOut,
    val items: List<RemoteInboxItem> = emptyList(),
    val unreadCount: Int = 0,
    val detail: RemoteInboxItem? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val permission: Boolean = true
)

sealed interface RemoteInboxAction {
    data object Refresh : RemoteInboxAction
    data object LoadNext : RemoteInboxAction
    data class Open(val messageId: String) : RemoteInboxAction
    data class MarkRead(val messageId: String) : RemoteInboxAction
    data object Back : RemoteInboxAction
    data object DismissError : RemoteInboxAction
}

sealed interface RemoteQuiesceResult {
    data object Quiesced : RemoteQuiesceResult
    data class TimedOut(val remainingJobs: Int) : RemoteQuiesceResult
}

data class RemoteNotificationCapabilities(
    val alertsSupported: Boolean,
    val actionsSupported: Boolean,
    val notificationsEnabled: Boolean
)

sealed interface RemotePostResult {
    data object Posted : RemotePostResult
    data object Suppressed : RemotePostResult
    data object PermissionDenied : RemotePostResult
    data class Failed(val reason: String) : RemotePostResult
}

interface RemoteNotificationAdapter {
    suspend fun capabilities(): RemoteNotificationCapabilities
    suspend fun show(messageId: String, generation: Long): RemotePostResult
    suspend fun clearRemote(generation: Long)
    suspend fun close()
    fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit)
}

interface RemoteInboxController {
    val state: StateFlow<RemoteInboxState>
    fun dispatch(action: RemoteInboxAction)
    suspend fun start(account: AccountIdentity, generation: Long)
    suspend fun quiesce(): RemoteQuiesceResult
    suspend fun close()
    fun onForeground()
    fun onNativeOpen(messageId: String, capturedGeneration: Long)
}
