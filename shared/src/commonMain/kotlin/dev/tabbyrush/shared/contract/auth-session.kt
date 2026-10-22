package dev.tabbyrush.shared.contract

import kotlinx.coroutines.flow.StateFlow

enum class AuthState {
    UNAUTHENTICATED,
    AUTHENTICATING,
    AUTHENTICATED,
    ACCESS_PENDING,
    REAUTH_REQUIRED
}

data class AccessRequestInfo(
    val id: String,
    val userId: String,
    val username: String,
    val appId: String,
    val message: String? = null,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class UserLookupResult(
    val userId: String,
    val username: String
)

interface AuthSession {
    val state: StateFlow<AuthState>
    val currentIdentity: StateFlow<AccountIdentity?>
    val currentAccountKey: StateFlow<AccountKey?>

    suspend fun register(username: String, email: String, password: String): ApiResult<AccountIdentity>
    suspend fun login(email: String, password: String): ApiResult<AccountIdentity>
    suspend fun refresh(): ApiResult<Unit>
    suspend fun checkAccess(): ApiResult<AccessRequestInfo?>
    suspend fun requestAccess(message: String? = null): ApiResult<AccessRequestInfo>
    suspend fun lookupUser(username: String): ApiResult<UserLookupResult>
    suspend fun logout()
    fun close()
}
