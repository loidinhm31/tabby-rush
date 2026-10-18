package dev.tabbyrush.shared.ui

/** Validates whether [id] is a canonical 36-character lowercase UUID. */
fun isCanonicalUuid(id: String): Boolean {
    val trimmed = id.trim()
    if (trimmed.length != 36) return false
    for (i in 0 until 36) {
        val c = trimmed[i]
        when (i) {
            8, 13, 18, 23 -> if (c != '-') return false
            else -> if (c !in '0'..'9' && c !in 'a'..'f') return false
        }
    }
    return true
}

/** Pure navigation routes for remote inbox and main task workspace. */
sealed interface RemoteInboxRoute {
    data object Tasks : RemoteInboxRoute
    data object List : RemoteInboxRoute
    data class Detail(val messageId: String) : RemoteInboxRoute
}

/** Immutable navigation state for remote announcements and task workspace integration. */
data class RemoteInboxNavState(
    val currentRoute: RemoteInboxRoute = RemoteInboxRoute.Tasks,
    val backStack: List<RemoteInboxRoute> = emptyList(),
    val unavailableMessage: String? = null,
    val generation: Long = 0L,
    val pendingDetailId: String? = null
) {
    fun navigateToList(): RemoteInboxNavState {
        if (currentRoute == RemoteInboxRoute.List) return copy(unavailableMessage = null)
        val newStack = when {
            currentRoute == RemoteInboxRoute.Tasks -> listOf(RemoteInboxRoute.Tasks)
            !backStack.contains(currentRoute) -> backStack + currentRoute
            else -> backStack
        }
        return copy(currentRoute = RemoteInboxRoute.List, backStack = newStack, unavailableMessage = null, pendingDetailId = null)
    }

    fun navigateToDetail(messageId: String): RemoteInboxNavState {
        val trimmed = messageId.trim().lowercase()
        if (!isCanonicalUuid(trimmed)) {
            return copy(
                currentRoute = RemoteInboxRoute.List,
                unavailableMessage = "The requested announcement is unavailable or invalid.",
                pendingDetailId = null
            )
        }
        val newStack = when (currentRoute) {
            RemoteInboxRoute.Tasks -> listOf(RemoteInboxRoute.Tasks, RemoteInboxRoute.List)
            RemoteInboxRoute.List -> if (backStack.isEmpty()) listOf(RemoteInboxRoute.Tasks, RemoteInboxRoute.List) else backStack + RemoteInboxRoute.List
            else -> backStack
        }
        return copy(currentRoute = RemoteInboxRoute.Detail(trimmed), backStack = newStack, unavailableMessage = null, pendingDetailId = trimmed)
    }

    fun navigateToTasks(): RemoteInboxNavState =
        copy(currentRoute = RemoteInboxRoute.Tasks, backStack = emptyList(), unavailableMessage = null, pendingDetailId = null)

    fun popBack(): RemoteInboxNavState {
        if (backStack.isNotEmpty()) {
            val prev = backStack.last()
            return copy(
                currentRoute = prev,
                backStack = backStack.dropLast(1),
                unavailableMessage = null,
                pendingDetailId = if (prev is RemoteInboxRoute.Detail) prev.messageId else null
            )
        }
        return when (currentRoute) {
            is RemoteInboxRoute.Detail -> copy(currentRoute = RemoteInboxRoute.List, backStack = listOf(RemoteInboxRoute.Tasks), unavailableMessage = null, pendingDetailId = null)
            is RemoteInboxRoute.List -> copy(currentRoute = RemoteInboxRoute.Tasks, backStack = emptyList(), unavailableMessage = null, pendingDetailId = null)
            is RemoteInboxRoute.Tasks -> this
        }
    }

    fun onGenerationChanged(newGeneration: Long): RemoteInboxNavState =
        RemoteInboxNavState(currentRoute = RemoteInboxRoute.Tasks, backStack = emptyList(), unavailableMessage = null, generation = newGeneration, pendingDetailId = null)

    fun handleNativeEntry(
        appId: String?,
        messageId: String?,
        targetGeneration: Long,
        currentGeneration: Long,
        isAuthenticated: Boolean
    ): RemoteInboxNavState {
        if (!isAuthenticated) {
            return copy(currentRoute = RemoteInboxRoute.List, backStack = listOf(RemoteInboxRoute.Tasks), unavailableMessage = "Sign in to view announcement details.", generation = currentGeneration, pendingDetailId = null)
        }
        if (targetGeneration != currentGeneration) {
            return copy(currentRoute = RemoteInboxRoute.List, backStack = listOf(RemoteInboxRoute.Tasks), unavailableMessage = "The announcement is not available for the active account.", generation = currentGeneration, pendingDetailId = null)
        }
        if (appId != null && appId != "tabby-rush") {
            return copy(currentRoute = RemoteInboxRoute.List, backStack = listOf(RemoteInboxRoute.Tasks), unavailableMessage = "The requested announcement is unavailable.", generation = currentGeneration, pendingDetailId = null)
        }
        val trimmed = messageId?.trim()?.lowercase()
        if (trimmed.isNullOrBlank() || !isCanonicalUuid(trimmed)) {
            return copy(currentRoute = RemoteInboxRoute.List, backStack = listOf(RemoteInboxRoute.Tasks), unavailableMessage = "The requested announcement is unavailable or invalid.", generation = currentGeneration, pendingDetailId = null)
        }
        return copy(currentRoute = RemoteInboxRoute.Detail(trimmed), backStack = listOf(RemoteInboxRoute.Tasks, RemoteInboxRoute.List), unavailableMessage = null, generation = currentGeneration, pendingDetailId = trimmed)
    }

    fun dismissUnavailableMessage(): RemoteInboxNavState = copy(unavailableMessage = null)
}

/** Parses route string or URI into [RemoteInboxRoute], rejecting malformed or unauthorized paths. */
fun parseRemoteInboxRoute(uriOrPath: String): RemoteInboxRoute? {
    val clean = uriOrPath.trim()
    if (clean.isEmpty()) return null
    val path = when {
        clean.startsWith("tabbyrush://") -> {
            val after = clean.removePrefix("tabbyrush://")
            when {
                after == "tasks" || after.startsWith("tasks/") -> after
                after == "inbox" || after.startsWith("inbox/") -> after
                else -> return null
            }
        }
        clean.startsWith("/") -> clean.removePrefix("/")
        else -> clean
    }
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when (segments.size) {
        1 -> when (segments[0].lowercase()) {
            "tasks" -> RemoteInboxRoute.Tasks
            "inbox" -> RemoteInboxRoute.List
            else -> null
        }
        2 -> {
            if (segments[0].lowercase() == "inbox") {
                val id = segments[1].lowercase()
                if (isCanonicalUuid(id)) RemoteInboxRoute.Detail(id) else null
            } else null
        }
        else -> null
    }
}
