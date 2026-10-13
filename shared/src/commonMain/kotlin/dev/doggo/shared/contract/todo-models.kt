package dev.doggo.shared.contract

enum class TodoFilter {
    ALL,
    ACTIVE,
    COMPLETED
}

data class Todo(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val serverRevision: Long = 0,
    val isPending: Boolean = false,
    val isDeleted: Boolean = false
) {
    init {
        require(createdAt >= 0) { "createdAt must be non-negative" }
        require(updatedAt >= createdAt) { "updatedAt must be greater than or equal to createdAt" }
        require(serverRevision >= 0) { "serverRevision must be non-negative" }
    }
}

data class TodoCounts(
    val all: Int,
    val active: Int,
    val completed: Int
) {
    init {
        require(all >= 0 && active >= 0 && completed >= 0) { "Counts must be non-negative" }
        require(all == active + completed) { "Total count must equal active + completed" }
    }
}
