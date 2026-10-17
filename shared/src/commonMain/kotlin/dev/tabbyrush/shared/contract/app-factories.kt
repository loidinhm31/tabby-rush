package dev.tabbyrush.shared.contract

interface AppFactories {
    fun createCredentialVault(): CredentialVault
    fun createAccountStoreFactory(): AccountStoreFactory
    fun createSyncScheduler(): SyncScheduler
}
