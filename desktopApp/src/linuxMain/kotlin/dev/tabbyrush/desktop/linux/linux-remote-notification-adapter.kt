package dev.tabbyrush.desktop.linux

import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Linux desktop implementation of RemoteNotificationAdapter.
 * Bridges remote server announcements to the persistent Freedesktop D-Bus notification subsystem.
 *
 * Requirements:
 * - Persistent session D-Bus connection with ActionInvoked, NotificationClosed, NameOwnerChanged subscriptions.
 * - Capabilities probe: if 'actions' supported -> supply ['default', 'Open inbox']; if not -> report capability limitation.
 * - Generic alert copy only (fixed title and body, no private announcement content in OS toasts).
 * - Bounded mapping (busEpoch, nativeId) -> (messageId, accountGeneration) capped at 200 items; evicts oldest on cap.
 * - Action handling: filter 'default' action, double-fence against closed/stale generation, marshal to UI dispatcher.
 * - Daemon owner change / disconnect: increment epoch, drop stale mappings, reconnect bounded backoff (1s..30s), no replay.
 * - clearRemote: close only remote-owned notification IDs; never touch reminder IDs.
 * - Explicit lifecycle and handler cleanup on close.
 */
class LinuxRemoteNotificationAdapter(
    private val transport: PersistentNotificationTransport = DbusPersistentNotificationTransport(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onActivationRequest: (() -> Unit)? = null
) : RemoteNotificationAdapter, NotificationSignalListener {

    companion object {
        const val MAX_REMOTE_NOTIFICATIONS = 200
        const val GENERIC_TITLE = "TabbyRush Announcement"
        const val GENERIC_BODY = "You have a new announcement in TabbyRush."
        const val APP_NAME = "TabbyRush"
        const val APP_ICON = "tabbyrush"
        val REMOTE_ACTIONS = listOf("default", "Open inbox")
    }

    data class TrackedNotification(
        val messageId: String,
        val generation: Long,
        val busEpoch: Long,
        val nativeId: Long
    )

    private val isClosed = AtomicBoolean(false)
    private val activeGeneration = AtomicLong(0L)
    private val highestClearedGeneration = AtomicLong(-1L)
    private val busEpoch = AtomicLong(1L)
    private val currentDaemonOwner = AtomicReference<String?>(null)

    private val mutex = Mutex()
    private val remoteMap = LinkedHashMap<Pair<Long, Long>, TrackedNotification>()
    private val earlyActionSignals = ConcurrentHashMap<Pair<Long, Long>, Long>()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var reconnectJob: Job? = null

    @Volatile
    private var clientCallback: ((messageId: String, generation: Long) -> Unit)? = null

    @Volatile
    private var cachedCapabilities: RemoteNotificationCapabilities? = null

    init {
        transport.setSignalListener(this)
        if (transport.isConnected()) {
            currentDaemonOwner.set(transport.getDaemonOwner())
        }
    }

    override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {
        clientCallback = callback
    }

    override suspend fun capabilities(): RemoteNotificationCapabilities = withContext(ioDispatcher) {
        if (isClosed.get()) {
            return@withContext RemoteNotificationCapabilities(
                alertsSupported = false,
                actionsSupported = false,
                notificationsEnabled = false
            )
        }

        mutex.withLock {
            if (!transport.isConnected()) {
                val connected = try {
                    transport.connect()
                } catch (_: Exception) {
                    false
                }
                if (!connected) {
                    return@withLock RemoteNotificationCapabilities(
                        alertsSupported = false,
                        actionsSupported = false,
                        notificationsEnabled = false
                    ).also { cachedCapabilities = it }
                }
                currentDaemonOwner.set(transport.getDaemonOwner())
            } else if (currentDaemonOwner.get() == null) {
                currentDaemonOwner.set(transport.getDaemonOwner())
            }

            probeCapabilitiesLocked()
        }
    }

    private fun probeCapabilitiesLocked(): RemoteNotificationCapabilities {
        return try {
            val caps = transport.getCapabilities()
            val actions = caps.contains("actions")
            RemoteNotificationCapabilities(
                alertsSupported = true,
                actionsSupported = actions,
                notificationsEnabled = true
            ).also { cachedCapabilities = it }
        } catch (_: Exception) {
            RemoteNotificationCapabilities(
                alertsSupported = false,
                actionsSupported = false,
                notificationsEnabled = false
            ).also { cachedCapabilities = it }
        }
    }

    override suspend fun show(messageId: String, generation: Long): RemotePostResult = withContext(ioDispatcher) {
        if (isClosed.get()) {
            return@withContext RemotePostResult.Failed("Remote notification adapter is closed")
        }

        if (generation <= highestClearedGeneration.get()) {
            return@withContext RemotePostResult.Failed(
                "Stale or cleared generation $generation (highest cleared: ${highestClearedGeneration.get()})"
            )
        }

        activeGeneration.set(maxOf(activeGeneration.get(), generation))

        mutex.withLock {
            if (!transport.isConnected()) {
                val ok = try {
                    transport.connect()
                } catch (_: Exception) {
                    false
                }
                if (!ok) {
                    return@withLock RemotePostResult.Failed("Notification daemon unavailable")
                }
                currentDaemonOwner.set(transport.getDaemonOwner())
                probeCapabilitiesLocked()
            } else if (currentDaemonOwner.get() == null) {
                currentDaemonOwner.set(transport.getDaemonOwner())
            }
        }

        val caps = cachedCapabilities ?: mutex.withLock { probeCapabilitiesLocked() }
        if (!caps.alertsSupported) {
            return@withContext RemotePostResult.Failed("Notification daemon unavailable")
        }

        val actions = if (caps.actionsSupported) REMOTE_ACTIONS else emptyList()
        val capturedEpoch = busEpoch.get()

        val nativeId = try {
            transport.postNotification(
                appName = APP_NAME,
                replacesId = 0L,
                appIcon = APP_ICON,
                title = GENERIC_TITLE,
                body = GENERIC_BODY,
                actions = actions,
                expireTimeoutMs = -1
            )
        } catch (e: Exception) {
            return@withContext RemotePostResult.Failed("Delivery error: ${e.message}")
        }

        var evictedOldestId: Long? = null
        var shouldTriggerEarlyAction = false

        synchronized(remoteMap) {
            if (isClosed.get() || busEpoch.get() != capturedEpoch || generation <= highestClearedGeneration.get()) {
                runCatching { transport.closeNotification(nativeId) }
                return@withContext RemotePostResult.Failed("State changed during post")
            }

            if (remoteMap.size >= MAX_REMOTE_NOTIFICATIONS) {
                val oldestKey = remoteMap.keys.firstOrNull()
                if (oldestKey != null) {
                    val oldest = remoteMap.remove(oldestKey)
                    if (oldest != null) {
                        evictedOldestId = oldest.nativeId
                    }
                }
            }

            val entry = TrackedNotification(
                messageId = messageId,
                generation = generation,
                busEpoch = capturedEpoch,
                nativeId = nativeId
            )
            remoteMap[capturedEpoch to nativeId] = entry

            if (earlyActionSignals.remove(capturedEpoch to nativeId) != null) {
                shouldTriggerEarlyAction = true
            }
        }

        evictedOldestId?.let { oldestId ->
            runCatching { transport.closeNotification(oldestId) }
        }

        if (shouldTriggerEarlyAction) {
            dispatchActivation(messageId, generation, capturedEpoch)
        }

        RemotePostResult.Posted
    }

    override suspend fun clearRemote(generation: Long): Unit = withContext(ioDispatcher) {
        highestClearedGeneration.set(maxOf(highestClearedGeneration.get(), generation))
        if (activeGeneration.get() <= generation) {
            activeGeneration.set(generation + 1)
        }

        val toClose = mutableListOf<Long>()
        synchronized(remoteMap) {
            val iter = remoteMap.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (entry.value.generation <= generation) {
                    toClose.add(entry.value.nativeId)
                    iter.remove()
                }
            }
        }

        for (id in toClose) {
            runCatching { transport.closeNotification(id) }
        }
    }

    override suspend fun close() {
        isClosed.set(true)
        clientCallback = null
        withContext(ioDispatcher) {
            reconnectJob?.cancel()
            reconnectJob = null

            val toClose = mutableListOf<Long>()
            synchronized(remoteMap) {
                for (entry in remoteMap.values) {
                    toClose.add(entry.nativeId)
                }
                remoteMap.clear()
            }
            earlyActionSignals.clear()

            for (id in toClose) {
                runCatching { transport.closeNotification(id) }
            }

            runCatching { transport.close() }
            scope.cancel()
        }
    }

    override fun onActionInvoked(id: Long, action: String, sender: String?) {
        if (isClosed.get()) return
        if (action != "default") return

        val expectedOwner = currentDaemonOwner.get()
        if (sender != null && expectedOwner != null && sender != expectedOwner) {
            return
        }

        val capturedEpoch = busEpoch.get()
        val entry = synchronized(remoteMap) {
            remoteMap[capturedEpoch to id]
        }

        if (entry == null) {
            earlyActionSignals[capturedEpoch to id] = System.currentTimeMillis()
            return
        }

        if (entry.generation <= highestClearedGeneration.get() || entry.generation != activeGeneration.get()) {
            return
        }

        dispatchActivation(entry.messageId, entry.generation, capturedEpoch)
    }

    private fun dispatchActivation(messageId: String, generation: Long, capturedEpoch: Long) {
        scope.launch(uiDispatcher) {
            if (isClosed.get()) return@launch
            if (busEpoch.get() != capturedEpoch) return@launch
            if (activeGeneration.get() != generation) return@launch
            if (generation <= highestClearedGeneration.get()) return@launch

            onActivationRequest?.invoke()
            clientCallback?.invoke(messageId, generation)
        }
    }

    override fun onNotificationClosed(id: Long, reason: Long, sender: String?) {
        if (isClosed.get()) return
        val expectedOwner = currentDaemonOwner.get()
        if (sender != null && expectedOwner != null && sender != expectedOwner) {
            return
        }

        val capturedEpoch = busEpoch.get()
        synchronized(remoteMap) {
            remoteMap.remove(capturedEpoch to id)
        }
        earlyActionSignals.remove(capturedEpoch to id)
    }

    override fun onNameOwnerChanged(name: String, oldOwner: String, newOwner: String) {
        if (name != "org.freedesktop.Notifications") return
        if (isClosed.get()) return

        busEpoch.incrementAndGet()
        currentDaemonOwner.set(newOwner.ifEmpty { null })
        cachedCapabilities = null

        synchronized(remoteMap) {
            remoteMap.clear()
        }
        earlyActionSignals.clear()

        if (newOwner.isNotEmpty()) {
            scope.launch {
                mutex.withLock {
                    probeCapabilitiesLocked()
                }
            }
        } else {
            scheduleReconnect()
        }
    }

    override fun onBusDisconnected(error: Throwable?) {
        if (isClosed.get()) return

        busEpoch.incrementAndGet()
        currentDaemonOwner.set(null)
        cachedCapabilities = null

        synchronized(remoteMap) {
            remoteMap.clear()
        }
        earlyActionSignals.clear()

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (isClosed.get()) return
        synchronized(this) {
            if (reconnectJob?.isActive == true) return
            reconnectJob = scope.launch {
                var delayMs = 500L
                val maxDelayMs = 30_000L
                while (!isClosed.get() && !transport.isConnected()) {
                    delay(delayMs)
                    if (isClosed.get()) break
                    try {
                        if (transport.connect()) {
                            val owner = transport.getDaemonOwner()
                            currentDaemonOwner.set(owner)
                            mutex.withLock {
                                probeCapabilitiesLocked()
                            }
                            break
                        }
                    } catch (_: Exception) {}
                    delayMs = minOf(delayMs * 2, maxDelayMs)
                }
            }
        }
    }
}
