package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.contract.VaultStatus
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, account-keyed in-memory CredentialVault for desktop controller and lifecycle unit tests.
 */
class InMemoryCredentialVault(
    override val status: VaultStatus = VaultStatus.READY
) : CredentialVault {

    private val storage = ConcurrentHashMap<AccountKey, CredentialBundle>()
    val clearedKeys: MutableList<AccountKey> = Collections.synchronizedList(mutableListOf<AccountKey>())

    override suspend fun load(accountKey: AccountKey): CredentialBundle? {
        return storage[accountKey]
    }

    override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle) {
        storage[accountKey] = bundle
    }

    override suspend fun clear(accountKey: AccountKey) {
        storage.remove(accountKey)
        clearedKeys.add(accountKey)
    }

    fun contains(accountKey: AccountKey): Boolean {
        return storage.containsKey(accountKey)
    }
}
