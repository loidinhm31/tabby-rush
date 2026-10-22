package dev.tabbyrush.shared.contract

sealed interface SafeError {
    val code: String
    val message: String

    data class Validation(
        override val code: String,
        override val message: String,
        val field: String? = null
    ) : SafeError

    data class Network(
        override val code: String,
        override val message: String
    ) : SafeError

    data class Unauthorized(
        override val code: String,
        override val message: String
    ) : SafeError

    data class AccessPending(
        override val code: String,
        override val message: String
    ) : SafeError

    data class Conflict(
        override val code: String,
        override val message: String,
        val taskRef: TaskRef? = null,
        val rowId: String = taskRef?.rowId ?: ""
    ) : SafeError

    data class ProtocolIncompatible(
        override val code: String,
        override val message: String
    ) : SafeError

    data class CollaborationRequired(
        override val code: String = "COLLABORATION_REQUIRED",
        override val message: String = "Application upgrade required for collaboration"
    ) : SafeError

    data class AccessRevisionChanged(
        override val code: String = "ACCESS_REVISION_CHANGED",
        override val message: String = "Access revision changed during synchronization"
    ) : SafeError

    data class AccessDenied(
        override val code: String = "ACCESS_DENIED",
        override val message: String = "Access denied for task",
        val taskRef: TaskRef? = null
    ) : SafeError

    data class OperationForbidden(
        override val code: String = "OPERATION_FORBIDDEN",
        override val message: String = "Operation forbidden on task",
        val taskRef: TaskRef? = null
    ) : SafeError

    data class MembershipDenied(
        override val code: String = "MEMBERSHIP_DENIED",
        override val message: String = "Application membership denied"
    ) : SafeError


    data class UncertainOutcome(
        override val code: String,
        override val message: String,
        val opId: String
    ) : SafeError

    data class Internal(
        override val code: String,
        override val message: String
    ) : SafeError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: SafeError) : ApiResult<Nothing>
}
