package dev.tabbyrush.shared.contract

import kotlinx.coroutines.flow.Flow

interface AccountStore {
    val accountKey: AccountKey

    suspend fun loadTodos(): List<Todo>
    suspend fun saveTodoLocally(todo: Todo)
    suspend fun deleteTodoLocally(id: String)
    suspend fun markDispatching(opId: String)
    suspend fun reconcileAccepted(opId: String, serverRevision: Long)
    suspend fun wipeLocalData()
    fun close()

    suspend fun getPersistedScheduleProjection(): List<PersistedTaskSchedule>
    fun observePersistedSchedule(): Flow<List<PersistedTaskSchedule>>
    fun observeTodos(): Flow<List<Todo>>
    suspend fun setCompletedLocally(id: String, completed: Boolean, nowEpochMs: Long): CompletionChange
}

interface AccountStoreFactory {
    fun open(accountKey: AccountKey): AccountStore
}
