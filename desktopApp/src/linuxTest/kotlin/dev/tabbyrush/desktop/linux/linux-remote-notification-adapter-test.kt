package dev.tabbyrush.desktop.linux

import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LinuxRemoteNotificationAdapterTest {

    private class FakePersistentNotificationTransport(
        var connected: Boolean = true,
        var currentCapabilities: Set<String> = setOf("body", "actions", "icon-static"),
        var currentDaemonOwner: String? = ":1.42",
        var postException: Exception? = null,
        var connectException: Exception? = null
    ) : PersistentNotificationTransport {
        var nextId = 1L
        val postedNotifications = mutableListOf<PostedNotification>()
        val closedNotificationIds = mutableListOf<Long>()
        var currentSignalListener: NotificationSignalListener? = null
        var isClosed = false

        data class PostedNotification(
            val appName: String,
            val replacesId: Long,
            val appIcon: String,
            val title: String,
            val body: String,
            val actions: List<String>,
            val expireTimeoutMs: Int,
            val id: Long
        )

        override fun isConnected(): Boolean = connected && !isClosed

        override fun getCapabilities(): Set<String> {
            if (!isConnected()) throw IllegalStateException("Not connected")
            return currentCapabilities
        }

        override fun getDaemonOwner(): String? = currentDaemonOwner

        override fun postNotification(
            appName: String,
            replacesId: Long,
            appIcon: String,
            title: String,
            body: String,
            actions: List<String>,
            expireTimeoutMs: Int
        ): Long {
            if (!isConnected()) throw IllegalStateException("Not connected")
            postException?.let { throw it }
            val id = nextId++
            postedNotifications.add(
                PostedNotification(appName, replacesId, appIcon, title, body, actions, expireTimeoutMs, id)
            )
            return id
        }

        override fun closeNotification(id: Long) {
            closedNotificationIds.add(id)
        }

        override fun setSignalListener(listener: NotificationSignalListener?) {
            currentSignalListener = listener
        }

        override fun connect(): Boolean {
            connectException?.let { throw it }
            connected = true
            return true
        }

        override fun disconnect() {
            connected = false
        }

        override fun close() {
            isClosed = true
            connected = false
        }

        fun simulateActionInvoked(id: Long, action: String, sender: String? = currentDaemonOwner) {
            currentSignalListener?.onActionInvoked(id, action, sender)
        }

        fun simulateNotificationClosed(id: Long, reason: Long = 2L, sender: String? = currentDaemonOwner) {
            currentSignalListener?.onNotificationClosed(id, reason, sender)
        }

        fun simulateNameOwnerChanged(oldOwner: String, newOwner: String) {
            currentDaemonOwner = newOwner.ifEmpty { null }
            currentSignalListener?.onNameOwnerChanged("org.freedesktop.Notifications", oldOwner, newOwner)
        }

        fun simulateBusDisconnected(error: Throwable? = null) {
            connected = false
            currentSignalListener?.onBusDisconnected(error)
        }
    }

    @Test
    fun testCapabilitiesWithActionsSupported() = runTest {
        val fake = FakePersistentNotificationTransport(
            currentCapabilities = setOf("body", "actions", "icon-static")
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        val caps = adapter.capabilities()
        assertTrue(caps.alertsSupported)
        assertTrue(caps.actionsSupported)
        assertTrue(caps.notificationsEnabled)
    }

    @Test
    fun testCapabilitiesWithoutActionsSupported() = runTest {
        val fake = FakePersistentNotificationTransport(
            currentCapabilities = setOf("body", "icon-static")
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        val caps = adapter.capabilities()
        assertTrue(caps.alertsSupported)
        assertFalse(caps.actionsSupported, "Actions must be reported unsupported when daemon lacks actions")
        assertTrue(caps.notificationsEnabled)
    }

    @Test
    fun testCapabilitiesDaemonUnavailable() = runTest {
        val fake = FakePersistentNotificationTransport(
            connected = false,
            currentDaemonOwner = null
        )
        fake.connectException = IllegalStateException("Cannot connect to session bus")
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        val caps = adapter.capabilities()
        assertFalse(caps.alertsSupported)
        assertFalse(caps.actionsSupported)
        assertFalse(caps.notificationsEnabled)
    }

    @Test
    fun testShowPostsGenericCopyAndInboxAction() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        val result = adapter.show("msg-1", 1L)
        assertIs<RemotePostResult.Posted>(result)

        assertEquals(1, fake.postedNotifications.size)
        val posted = fake.postedNotifications.first()
        assertEquals("TabbyRush Announcement", posted.title)
        assertEquals("You have a new announcement in TabbyRush.", posted.body)
        assertEquals("TabbyRush", posted.appName)
        assertEquals("tabbyrush", posted.appIcon)
        assertEquals(listOf("default", "Open inbox"), posted.actions)
    }

    @Test
    fun testShowWithoutActionsPostsEmptyActions() = runTest {
        val fake = FakePersistentNotificationTransport(
            currentCapabilities = setOf("body")
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        val result = adapter.show("msg-1", 1L)
        assertIs<RemotePostResult.Posted>(result)

        assertEquals(1, fake.postedNotifications.size)
        val posted = fake.postedNotifications.first()
        assertTrue(posted.actions.isEmpty(), "Actions must be empty when daemon does not support actions")
    }

    @Test
    fun testActionInvokedTriggersCallbackWithGeneration() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var activatedMsgId: String? = null
        var activatedGen: Long? = null
        adapter.setActivationCallback { messageId, generation ->
            activatedMsgId = messageId
            activatedGen = generation
        }

        adapter.show("msg-42", 1L)
        testScheduler.advanceUntilIdle()

        fake.simulateActionInvoked(id = 1L, action = "default")
        testScheduler.advanceUntilIdle()

        assertEquals("msg-42", activatedMsgId)
        assertEquals(1L, activatedGen)
    }

    @Test
    fun testNonDefaultActionIsIgnored() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var invoked = false
        adapter.setActivationCallback { _, _ ->
            invoked = true
        }

        adapter.show("msg-1", 1L)
        testScheduler.advanceUntilIdle()

        fake.simulateActionInvoked(id = 1L, action = "dismiss")
        testScheduler.advanceUntilIdle()

        assertFalse(invoked, "Non-default actions must be ignored")
    }

    @Test
    fun testActionFromOlderGenerationIsDiscarded() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var activatedMsgId: String? = null
        adapter.setActivationCallback { messageId, _ ->
            activatedMsgId = messageId
        }

        adapter.show("msg-1", 1L)
        testScheduler.advanceUntilIdle()

        // Clear generation 1, advancing generation boundary
        adapter.clearRemote(1L)
        testScheduler.advanceUntilIdle()

        // Show generation 2
        adapter.show("msg-2", 2L)
        testScheduler.advanceUntilIdle()

        // Action from generation 1 arrives
        fake.simulateActionInvoked(id = 1L, action = "default")
        testScheduler.advanceUntilIdle()

        assertNull(activatedMsgId, "Actions from older cleared generation must be discarded")

        // Action from generation 2 arrives
        fake.simulateActionInvoked(id = 2L, action = "default")
        testScheduler.advanceUntilIdle()

        assertEquals("msg-2", activatedMsgId)
    }

    @Test
    fun testDoubleFencingOnUiDispatcherPreventsStaleExecution() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var invoked = false
        adapter.setActivationCallback { _, _ ->
            invoked = true
        }

        adapter.show("msg-1", 1L)
        testScheduler.advanceUntilIdle()

        // Simulate action invoked (queues on uiDispatcher)
        fake.simulateActionInvoked(id = 1L, action = "default")

        // Before uiDispatcher runs the queued task, adapter is closed
        adapter.close()

        // Now run the queued task on dispatcher
        testScheduler.advanceUntilIdle()

        assertFalse(invoked, "Callback must not be invoked if adapter was closed while queued on UI dispatcher")
    }

    @Test
    fun testDaemonOwnerChangeIncrementsEpochAndClearsMappings() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var activatedMsgId: String? = null
        adapter.setActivationCallback { messageId, _ ->
            activatedMsgId = messageId
        }

        adapter.show("msg-1", 1L)
        testScheduler.advanceUntilIdle()

        // Daemon restarts with new owner name
        fake.simulateNameOwnerChanged(oldOwner = ":1.42", newOwner = ":1.99")
        testScheduler.advanceUntilIdle()

        // Late signal from old daemon native ID 1
        fake.simulateActionInvoked(id = 1L, action = "default", sender = ":1.42")
        testScheduler.advanceUntilIdle()

        assertNull(activatedMsgId, "Old notification mapping must be purged on daemon owner change")
    }

    @Test
    fun testStaleClearDoesNotInvalidateNewerGeneration() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var activatedMsgId: String? = null
        adapter.setActivationCallback { messageId, _ ->
            activatedMsgId = messageId
        }

        adapter.show("msg-5", 5L)
        testScheduler.advanceUntilIdle()

        // Stale clear for an earlier generation
        adapter.clearRemote(2L)
        testScheduler.advanceUntilIdle()

        // Generation 5 action still activates
        fake.simulateActionInvoked(id = 1L, action = "default")
        testScheduler.advanceUntilIdle()

        assertEquals("msg-5", activatedMsgId, "Stale clear must not invalidate newer active generation")
    }

    @Test
    fun testSignalBeforeReplyHandling() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var activatedMsgId: String? = null
        adapter.setActivationCallback { messageId, _ ->
            activatedMsgId = messageId
        }

        // Early signal arrives before show finishes inserting ID 1
        fake.simulateActionInvoked(id = 1L, action = "default")

        // Now show finishes with native ID 1
        adapter.show("msg-early", 1L)
        testScheduler.advanceUntilIdle()

        assertEquals("msg-early", activatedMsgId, "Early signal arriving before reply completion must be processed")
    }

    @Test
    fun testSpoofedSignalSenderDiscarded() = runTest {
        val fake = FakePersistentNotificationTransport(currentDaemonOwner = ":1.42")
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var invoked = false
        adapter.setActivationCallback { _, _ ->
            invoked = true
        }

        adapter.show("msg-1", 1L)
        testScheduler.advanceUntilIdle()

        // Spoofed signal with wrong sender
        fake.simulateActionInvoked(id = 1L, action = "default", sender = ":1.evil")
        testScheduler.advanceUntilIdle()

        assertFalse(invoked, "Signal from spoofed sender must be discarded")
    }

    @Test
    fun testMaxCapacityEvictionClosesOldest() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        for (i in 1..201) {
            adapter.show("msg-$i", 1L)
        }
        testScheduler.advanceUntilIdle()

        // The 1st notification (native ID 1) must have been evicted and closed
        assertTrue(fake.closedNotificationIds.contains(1L), "Oldest notification must be closed when cap is exceeded")
    }

    @Test
    fun testNotificationClosedRemovesMapping() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        var invoked = false
        adapter.setActivationCallback { _, _ ->
            invoked = true
        }

        adapter.show("msg-1", 1L)
        testScheduler.advanceUntilIdle()

        // Notification closed by user
        fake.simulateNotificationClosed(id = 1L, reason = 2L)
        testScheduler.advanceUntilIdle()

        // Action signal arrives after close
        fake.simulateActionInvoked(id = 1L, action = "default")
        testScheduler.advanceUntilIdle()

        assertFalse(invoked, "Closed notifications must not trigger activation callbacks")
    }

    @Test
    fun testClearRemoteClosesOnlyRemoteNotifications() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        adapter.show("msg-1", 1L) // ID 1
        adapter.show("msg-2", 1L) // ID 2
        adapter.show("msg-3", 2L) // ID 3
        testScheduler.advanceUntilIdle()

        adapter.clearRemote(1L)
        testScheduler.advanceUntilIdle()

        assertTrue(fake.closedNotificationIds.contains(1L))
        assertTrue(fake.closedNotificationIds.contains(2L))
        assertFalse(fake.closedNotificationIds.contains(3L), "clearRemote must not close IDs of newer generations")
    }

    @Test
    fun testCloseDrainsAndClosesOwned() = runTest {
        val fake = FakePersistentNotificationTransport()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = LinuxRemoteNotificationAdapter(
            transport = fake,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher
        )

        adapter.show("msg-1", 1L)
        adapter.show("msg-2", 1L)
        testScheduler.advanceUntilIdle()

        adapter.close()
        testScheduler.advanceUntilIdle()

        assertTrue(fake.closedNotificationIds.contains(1L))
        assertTrue(fake.closedNotificationIds.contains(2L))
        assertTrue(fake.isClosed, "Transport must be closed on adapter close")

        val result = adapter.show("msg-3", 1L)
        assertIs<RemotePostResult.Failed>(result)
    }

    @Test
    fun testHostEnvironmentProbeSafeExecution() = runTest {
        val adapter = LinuxRemoteNotificationAdapter()
        val caps = adapter.capabilities()
        // Probing host environment must complete safely without throwing unhandled exception
        assertTrue(caps.alertsSupported || !caps.alertsSupported)
    }
}
