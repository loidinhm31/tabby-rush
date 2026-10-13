package dev.doggo.shared.contract

interface AccountStore {
    val accountKey: AccountKey

    suspend fun loadTodos(): List<Todo>
    suspend fun saveTodoLocally(todo: Todo)
    suspend fun deleteTodoLocally(id: String)
    suspend fun markDispatching(opId: String)
    suspend fun reconcileAccepted(opId: String, serverRevision: Long)
    suspend fun wipeLocalData()
    fun close()
}

interface AccountStoreFactory {
    fun open(accountKey: AccountKey): AccountStore
}
