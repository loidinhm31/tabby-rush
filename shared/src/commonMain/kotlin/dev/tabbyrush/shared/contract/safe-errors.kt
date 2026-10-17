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
        val rowId: String
    ) : SafeError

    data class ProtocolIncompatible(
        override val code: String,
        override val message: String
    ) : SafeError

    data class ResetRequired(
        override val code: String,
        override val message: String
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
