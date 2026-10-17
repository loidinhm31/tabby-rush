package dev.tabbyrush.shared.contract

class CredentialBundle(
    val accessToken: String,
    val refreshToken: String
) {
    override fun toString(): String = "CredentialBundle([REDACTED])"
    override fun hashCode(): Int = accessToken.hashCode() * 31 + refreshToken.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialBundle) return false
        return accessToken == other.accessToken && refreshToken == other.refreshToken
    }
}

enum class VaultStatus {
    READY,
    LOCKED,
    CORRUPT,
    UNAVAILABLE
}

interface CredentialVault {
    val status: VaultStatus
    suspend fun load(accountKey: AccountKey): CredentialBundle?
    suspend fun store(accountKey: AccountKey, bundle: CredentialBundle)
    suspend fun clear(accountKey: AccountKey)
}
