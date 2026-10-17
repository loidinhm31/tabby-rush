package dev.tabbyrush.shared.contract

interface SyncScheduler {
    fun schedulePeriodic(accountKey: AccountKey)
    fun triggerImmediate(accountKey: AccountKey)
    fun cancel(accountKey: AccountKey)
}
