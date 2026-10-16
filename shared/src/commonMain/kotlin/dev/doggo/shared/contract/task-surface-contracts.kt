package dev.doggo.shared.contract

import kotlinx.coroutines.flow.StateFlow

enum class CompletionChange { Changed, Unchanged, Missing }

data class SurfaceAccountToken(val accountKey: AccountKey, val generation: Long)

sealed interface SurfaceReadiness {
    data object Restoring : SurfaceReadiness
    data class Ready(val token: SurfaceAccountToken) : SurfaceReadiness
    data object SignedOut : SurfaceReadiness
    data object Locked : SurfaceReadiness
    data object Unavailable : SurfaceReadiness
}

data class TaskSections(val incomplete: List<Todo>, val completed: List<Todo>)

data class SurfaceSnapshot(
    val token: SurfaceAccountToken,
    val sections: TaskSections,
    val schedules: List<PersistedTaskSchedule>
)

sealed interface SurfaceResult<out T> {
    data class Value<T>(val value: T) : SurfaceResult<T>
    data object Rejected : SurfaceResult<Nothing>
    data object Unavailable : SurfaceResult<Nothing>
}

interface TaskSurfaceAccess {
    val readiness: StateFlow<SurfaceReadiness>
    suspend fun awaitReadiness(timeoutMs: Long = 4_000L): SurfaceReadiness
    suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot>
    suspend fun setCompleted(
        token: SurfaceAccountToken,
        taskId: String,
        completed: Boolean,
        nowEpochMs: Long
    ): SurfaceResult<CompletionChange>
    suspend fun publishIfCurrent(token: SurfaceAccountToken, publish: suspend () -> Unit): Boolean
    suspend fun validateNavigation(token: SurfaceAccountToken, taskId: String?): Boolean
}
