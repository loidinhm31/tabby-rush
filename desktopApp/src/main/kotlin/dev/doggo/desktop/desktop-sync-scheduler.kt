package dev.doggo.desktop

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.SyncScheduler
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground-only sync scheduler for desktop lifecycle.
 * Manages periodic sync loops and one-off immediate sync requests within the window coroutine scope.
 *
 * Implements [LeaveSchedulerCancelGate] to guarantee in-flight sync jobs are completely quiesced
 * before account wipe or logout proceeds.
 */
class DesktopSyncScheduler(
    private val scope: CoroutineScope,
    private val periodicIntervalMillis: Long = 15 * 60 * 1000L, // 15 minutes
    private val syncRunner: suspend (AccountKey) -> Unit
) : SyncScheduler, LeaveSchedulerCancelGate {

    @Volatile
    private var isDisposed = false
    private val mutex = Mutex()

    private val periodicJobs = ConcurrentHashMap<String, Job>()
    private val immediateJobs = ConcurrentHashMap<String, Job>()

    override fun triggerImmediate(accountKey: AccountKey) {
        if (isDisposed) return

        scope.launch {
            mutex.withLock {
                if (isDisposed) return@launch
                val existing = immediateJobs[accountKey.value]
                if (existing != null && existing.isActive) {
                    return@launch
                }

                lateinit var currentJob: Job
                currentJob = scope.launch {
                    try {
                        syncRunner(accountKey)
                    } finally {
                        mutex.withLock {
                            if (immediateJobs[accountKey.value] === currentJob) {
                                immediateJobs.remove(accountKey.value)
                            }
                        }
                    }
                }
                immediateJobs[accountKey.value] = currentJob
            }
        }
    }

    override fun schedulePeriodic(accountKey: AccountKey) {
        if (isDisposed) return

        scope.launch {
            mutex.withLock {
                if (isDisposed) return@launch
                periodicJobs.remove(accountKey.value)?.cancel()

                val job = scope.launch {
                    while (isActive && !isDisposed) {
                        delay(periodicIntervalMillis)
                        try {
                            syncRunner(accountKey)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            // Suppress periodic network errors; coordinator publishes safe state
                        }
                    }
                }
                periodicJobs[accountKey.value] = job
            }
        }
    }

    override fun cancel(accountKey: AccountKey) {
        periodicJobs.remove(accountKey.value)?.cancel()
        immediateJobs.remove(accountKey.value)?.cancel()
    }

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        val (periodic, immediate) = mutex.withLock {
            val p = periodicJobs.remove(accountKey.value)
            val i = immediateJobs.remove(accountKey.value)
            Pair(p, i)
        }

        periodic?.cancel()
        immediate?.cancel()

        periodic?.join()
        immediate?.join()
    }

    suspend fun disposeAndAwait() {
        isDisposed = true
        val allJobs = mutex.withLock {
            val jobs = periodicJobs.values.toList() + immediateJobs.values.toList()
            periodicJobs.clear()
            immediateJobs.clear()
            jobs
        }

        allJobs.forEach { it.cancel() }
        allJobs.forEach {
            try {
                it.join()
            } catch (_: Exception) {}
        }
    }

    fun cancelAll() {
        isDisposed = true
        periodicJobs.values.forEach { it.cancel() }
        immediateJobs.values.forEach { it.cancel() }
        periodicJobs.clear()
        immediateJobs.clear()
    }
}
