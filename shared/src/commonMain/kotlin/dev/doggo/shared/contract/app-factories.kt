package dev.doggo.shared.contract

interface AppFactories {
    fun createCredentialVault(): CredentialVault
    fun createAccountStoreFactory(): AccountStoreFactory
    fun createSyncScheduler(): SyncScheduler
}
