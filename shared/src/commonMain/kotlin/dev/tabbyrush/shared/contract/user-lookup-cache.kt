package dev.tabbyrush.shared.contract

/**
 * Thread-safe client cache for resolving user IDs to known usernames and vice versa.
 * Populated during authentication (current user) and user lookups.
 */
object UserLookupCache {
    private val userIdToUsername = mutableMapOf<String, String>()
    private val usernameToUserId = mutableMapOf<String, String>()

    @kotlin.jvm.Synchronized
    fun remember(userId: String?, username: String?) {
        if (!userId.isNullOrBlank() && !username.isNullOrBlank()) {
            userIdToUsername[userId] = username
            usernameToUserId[username.lowercase()] = userId
        }
    }

    @kotlin.jvm.Synchronized
    fun getUsername(userId: String?): String? {
        if (userId.isNullOrBlank()) return null
        return userIdToUsername[userId]
    }

    @kotlin.jvm.Synchronized
    fun getUserId(username: String?): String? {
        if (username.isNullOrBlank()) return null
        return usernameToUserId[username.lowercase()]
    }

    @kotlin.jvm.Synchronized
    fun clear() {
        userIdToUsername.clear()
        usernameToUserId.clear()
    }
}
