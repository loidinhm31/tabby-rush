package dev.tabbyrush.desktop.windows

import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Windows desktop implementation of RemoteNotificationAdapter.
 * Bridges remote server announcements to the Windows native C++/WinRT toast subsystem.
 *
 * Requirements:
 * - Scoped to reserved group "tr-remote" (<= 16 chars).
 * - Deterministic 16-character lowercase hex tag from SHA-256(messageId) with collision detection.
 * - Startup purge of old "tr-remote" notifications to prevent stale click exposure after crash.
 * - Serialized operations (show, clearRemote, close) preventing races or late posts.
 * - Generation fence ensuring stale generation shows or callbacks are discarded.
 * - Callback fencing on teardown/close so stale notifications cannot invoke account actions.
 * - Independent handle and bridge lifetime isolated from local reminder notifications.
 */
class WindowsRemoteNotificationAdapter(
    private val bridge: WindowsToastBridge = JnaWindowsToastBridge(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RemoteNotificationAdapter {

    companion object {
        const val REMOTE_GROUP = "tr-remote"

        internal fun computeTag(messageId: String, salt: Int = 0): String {
            val input = if (salt == 0) messageId else "$messageId#$salt"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return hex.take(16)
        }
    }

    private val isClosed = AtomicBoolean(false)
    private val activeGeneration = AtomicLong(0L)
    private val operationMutex = Mutex()
    private val tagToMessageId = ConcurrentHashMap<String, String>()
    private val messageIdToTag = ConcurrentHashMap<String, String>()

    @Volatile
    private var clientCallback: ((messageId: String, generation: Long) -> Unit)? = null

    init {
        // Startup cleanup: purge old "tr-remote" toasts left from prior session/crash before stream starts
        if (bridge.isAvailable()) {
            val clearStatus = bridge.clearGroup(REMOTE_GROUP)
            if (clearStatus is WindowsNotificationStatus.Error) {
                // Non-fatal warning recorded; does not crash process startup
                System.err.println("Warning: startup clearGroup(tr-remote) reported error: ${clearStatus.message}")
            }
        }

        // Register native bridge activation handler with generation and lifetime callback fencing
        bridge.registerActivationCallback { capturedMsgId, capturedGen ->
            if (isClosed.get()) {
                // Stale callback fenced after close: discard
                return@registerActivationCallback
            }
            if (capturedGen < activeGeneration.get()) {
                // Callback from an older fenced account generation: discard
                return@registerActivationCallback
            }

            val resolvedId = tagToMessageId[capturedMsgId] ?: capturedMsgId
            clientCallback?.invoke(resolvedId, capturedGen)
        }
    }

    override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {
        clientCallback = callback
    }

    override suspend fun capabilities(): RemoteNotificationCapabilities = withContext(ioDispatcher) {
        if (!bridge.isAvailable() || isClosed.get()) {
            return@withContext RemoteNotificationCapabilities(
                alertsSupported = false,
                actionsSupported = false,
                notificationsEnabled = false
            )
        }

        val setting = bridge.querySetting()
        val isEnabled = setting == WindowsNotificationSetting.ENABLED

        RemoteNotificationCapabilities(
            alertsSupported = true,
            actionsSupported = true,
            notificationsEnabled = isEnabled
        )
    }

    override suspend fun show(messageId: String, generation: Long): RemotePostResult = withContext(ioDispatcher) {
        operationMutex.withLock {
            if (isClosed.get()) {
                return@withLock RemotePostResult.Failed("Remote notification adapter is closed")
            }

            if (generation < activeGeneration.get()) {
                return@withLock RemotePostResult.Failed("Stale generation $generation (active: ${activeGeneration.get()})")
            }

            // Update active generation boundary
            activeGeneration.set(maxOf(activeGeneration.get(), generation))

            if (!bridge.isAvailable()) {
                return@withLock RemotePostResult.Failed("Native Windows toast bridge is unavailable")
            }

            val setting = bridge.querySetting()
            if (setting != WindowsNotificationSetting.ENABLED) {
                return@withLock RemotePostResult.PermissionDenied
            }

            val tag = resolveOrAssignTag(messageId)
            val status = bridge.showRemote(
                messageId = messageId,
                generation = generation,
                tag = tag,
                group = REMOTE_GROUP
            )

            when (status) {
                is WindowsNotificationStatus.Success -> RemotePostResult.Posted
                is WindowsNotificationStatus.Unavailable -> RemotePostResult.Failed(status.reason)
                is WindowsNotificationStatus.Error -> RemotePostResult.Failed(status.message)
                is WindowsNotificationStatus.Cleared -> RemotePostResult.Failed("Unexpected status Cleared on show")
            }
        }
    }

    override suspend fun clearRemote(generation: Long): Unit = withContext(ioDispatcher) {
        operationMutex.withLock {
            // Advance active generation boundary to fence any in-flight shows or callbacks from older generation
            activeGeneration.set(maxOf(activeGeneration.get(), generation + 1))
            tagToMessageId.clear()
            messageIdToTag.clear()
            if (bridge.isAvailable()) {
                bridge.clearGroup(REMOTE_GROUP)
            }
        }
    }

    override suspend fun close(): Unit = withContext(ioDispatcher) {
        operationMutex.withLock {
            if (isClosed.compareAndSet(false, true)) {
                clientCallback = null
                tagToMessageId.clear()
                messageIdToTag.clear()
                if (bridge.isAvailable()) {
                    bridge.clearGroup(REMOTE_GROUP)
                    bridge.destroy()
                }
            }
        }
    }

    private fun resolveOrAssignTag(messageId: String): String {
        messageIdToTag[messageId]?.let { return it }

        var salt = 0
        var candidate = computeTag(messageId, salt)

        // Collision detection in process-memory map
        while (true) {
            val existing = tagToMessageId.putIfAbsent(candidate, messageId)
            if (existing == null || existing == messageId) {
                // Successfully reserved tag without collision
                messageIdToTag[messageId] = candidate
                return candidate
            }
            // Collision detected! Advance salt to generate alternate unique 16-hex tag
            salt++
            candidate = computeTag(messageId, salt)
        }
    }
}
