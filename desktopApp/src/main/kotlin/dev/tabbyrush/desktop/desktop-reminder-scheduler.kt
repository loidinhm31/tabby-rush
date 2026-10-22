package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-scoped reminder scheduler for desktop lifecycle.
 *
 * Coordinates scheduled reminders while the application window is visible or minimized.
 * When the window is closed, the scheduler is cancelled and drained; no background daemon or
 * tray process remains.
 *
 * Invariants:
 * 1. Wall-clock deadlines are stored as absolute instants; loop wait is bounded by [clockCheckIntervalMs]
 *    (default 15s) to detect clock jumps or system suspend/resume cleanly without tick-scanning every task.
 * 2. On suspend/resume or restart, overdue occurrences beyond the 5-minute grace window are not delivered
 *    as a burst; delivery goes through [ReminderSchedulingCoordinator.deliverReminder] which enforces
 *    the durable 5-minute grace window claim check in SQLite.
 * 3. Schedule updates are replace-based: new desired alarms replace prior lists and wake the loop immediately.
 * 4. Implements [LeaveSchedulerCancelGate] to cleanly cancel and await quiescence before account leave or wipe.
 */
class DesktopReminderScheduler(
    val accountKey: AccountKey,
    private val reminderCoordinator: ReminderSchedulingCoordinator,
    private val scope: CoroutineScope,
    private val clockCheckIntervalMs: Long = 15_000L,
    private val ioDispatcher: CoroutineDispatcher? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : LeaveSchedulerCancelGate {

    private val stateMutex = Mutex()
    private val alarms = mutableListOf<ScheduledAlarmHandle>()
    private val updateChannel = Channel<List<ScheduledAlarmHandle>>(Channel.CONFLATED)

    @Volatile
    private var isDisposed = false
    private var wakeLoopJob: Job? = null

    /**
     * Starts the background wake loop in [scope].
     */
    fun start() {
        if (wakeLoopJob == null && !isDisposed) {
            wakeLoopJob = if (ioDispatcher != null) {
                scope.launch(ioDispatcher) { runWakeLoop() }
            } else {
                scope.launch { runWakeLoop() }
            }
        }
    }

    /**
     * Replaces the current desired alarm handles via an ordered conflated channel.
     */
    fun updateDesiredAlarms(newAlarms: List<ScheduledAlarmHandle>) {
        if (isDisposed) return
        updateChannel.trySend(newAlarms.sortedBy { it.triggerAtEpochMs })
    }
    /**
     * Returns a snapshot of pending scheduled alarm handles.
     */
    suspend fun getPendingAlarms(): List<ScheduledAlarmHandle> = stateMutex.withLock {
        alarms.toList()
    }

    suspend fun cancelAlarmsForTask(taskRef: TaskRef) = stateMutex.withLock {
        alarms.removeAll { it.taskRef == taskRef }
    }

    private suspend fun runWakeLoop() {
        while (scope.isActive && !isDisposed) {
            val pendingIncoming = updateChannel.tryReceive().getOrNull()
            if (pendingIncoming != null) {
                stateMutex.withLock {
                    alarms.clear()
                    alarms.addAll(pendingIncoming)
                }
            }
            val now = clock()
            val dueAlarms = mutableListOf<ScheduledAlarmHandle>()
            var nextWakeTime: Long? = null

            stateMutex.withLock {
                val iterator = alarms.iterator()
                while (iterator.hasNext()) {
                    val alarm = iterator.next()
                    if (alarm.triggerAtEpochMs <= now) {
                        dueAlarms.add(alarm)
                        iterator.remove()
                    }
                }
                nextWakeTime = alarms.minOfOrNull { it.triggerAtEpochMs }
            }

            for (alarm in dueAlarms) {
                if (isDisposed) break
                try {
                    reminderCoordinator.deliverReminder(
                        taskId = alarm.taskId,
                        scheduleToken = alarm.scheduleToken,
                        occurrenceIndex = alarm.occurrenceIndex,
                        triggerGeneration = alarm.generation,
                        nowEpochMs = clock()
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    // Suppress and continue delivering remaining due reminders
                }
            }

            if (dueAlarms.isNotEmpty() && !isDisposed) {
                try {
                    reminderCoordinator.reconcile(clock())
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {}
            }

            val waitTimeMs = if (nextWakeTime != null) {
                val delayToAlarm = (nextWakeTime!! - clock()).coerceAtLeast(0L)
                minOf(delayToAlarm, clockCheckIntervalMs)
            } else {
                clockCheckIntervalMs
            }

            if (waitTimeMs > 0 && !isDisposed) {
                try {
                    withTimeoutOrNull(waitTimeMs) {
                        val result = updateChannel.receiveCatching()
                        val incoming = result.getOrNull()
                        if (incoming != null) {
                            stateMutex.withLock {
                                alarms.clear()
                                alarms.addAll(incoming)
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    if (isDisposed) break
                    throw ce
                } catch (_: Exception) {}
            }
        }
    }

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        if (this.accountKey != accountKey) return
        isDisposed = true
        stateMutex.withLock {
            alarms.clear()
        }
        wakeLoopJob?.cancelAndJoin()
        wakeLoopJob = null
    }
    suspend fun disposeAndAwait() {
        cancelAndAwait(accountKey)
    }
}
