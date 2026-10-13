package dev.doggo.shared.contract

interface SyncScheduler {
    fun schedulePeriodic(accountKey: AccountKey)
    fun triggerImmediate(accountKey: AccountKey)
    fun cancel(accountKey: AccountKey)
}
