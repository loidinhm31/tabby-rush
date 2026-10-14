package dev.doggo.shared.auth

import dev.doggo.shared.contract.AccessRequestInfo
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.CredentialBundle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
) {
    override fun toString(): String = "RegisterRequest(username='$username', email='$email', password=[REDACTED])"
}

@Serializable
data class RegisterResponse(
    val userId: String,
    val accessToken: String,
    val refreshToken: String
) {
    fun toCredentialBundle(): CredentialBundle = CredentialBundle(accessToken, refreshToken)
    override fun toString(): String = "RegisterResponse(userId='$userId', accessToken=[REDACTED], refreshToken=[REDACTED])"
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
) {
    override fun toString(): String = "LoginRequest(email='$email', password=[REDACTED])"
}
@Serializable
data class LoginResponse(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val apps: List<String> = emptyList(),
    val isAdmin: Boolean = false
) {
    fun toCredentialBundle(): CredentialBundle = CredentialBundle(accessToken, refreshToken)
    override fun toString(): String = "LoginResponse(userId='$userId', accessToken=[REDACTED], refreshToken=[REDACTED], apps=$apps, isAdmin=$isAdmin)"
}

@Serializable
data class RefreshRequest(
    val refreshToken: String
) {
    override fun toString(): String = "RefreshRequest(refreshToken=[REDACTED])"
}
@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String
) {
    fun toCredentialBundle(): CredentialBundle = CredentialBundle(accessToken, refreshToken)
    override fun toString(): String = "RefreshResponse(accessToken=[REDACTED], refreshToken=[REDACTED])"
}

@Serializable
data class MeResponse(
    val userId: String,
    val username: String,
    val email: String,
    val apps: List<String> = emptyList(),
    val isAdmin: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val timezone: String? = null,
    val createdAt: Long = 0L
) {
    fun toAccountIdentity(): AccountIdentity = AccountIdentity(
        userId = userId,
        username = username,
        email = email
    )

    fun hasDoggoAccess(appId: String = "doggo"): Boolean = apps.contains(appId)
}

@Serializable
data class CreateAccessRequestBody(
    val appId: String = "doggo",
    val message: String? = null
)

@Serializable
data class AccessRequestDto(
    val id: String,
    val userId: String,
    val username: String,
    val appId: String,
    val message: String? = null,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toAccessRequestInfo(): AccessRequestInfo = AccessRequestInfo(
        id = id,
        userId = userId,
        username = username,
        appId = appId,
        message = message,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

object AccessRequestStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"

    fun isApproved(status: String): Boolean = status.equals(APPROVED, ignoreCase = true)
    fun isPending(status: String): Boolean = status.equals(PENDING, ignoreCase = true)
    fun isRejected(status: String): Boolean = status.equals(REJECTED, ignoreCase = true)
}

/**
 * UI validation helpers that mirror server byte-length rules without replacing server authority.
 * Email is trimmed and lowercased; password is NEVER trimmed or logged.
 */
object HubAuthValidation {
    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateUsername(username: String): Boolean {
        val bytes = username.encodeToByteArray()
        if (bytes.size !in 3..32) return false
        return username.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    fun validatePassword(password: String): Boolean {
        val bytes = password.encodeToByteArray()
        return bytes.size in 8..128
    }

    fun validateEmail(email: String): Boolean {
        val normalized = normalizeEmail(email)
        if (normalized.length < 3 || normalized.length > 254) return false
        val atIndex = normalized.indexOf('@')
        if (atIndex <= 0 || atIndex != normalized.lastIndexOf('@') || atIndex == normalized.length - 1) {
            return false
        }
        val domain = normalized.substring(atIndex + 1)
        return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
    }
}
