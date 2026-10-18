package dev.tabbyrush.desktop.windows

import dev.tabbyrush.shared.contract.RemotePostResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WindowsRemoteNotificationAdapterTest {

    @Test
    fun testStartupClearsRemoteGroup() = runTest {
        val fakeBridge = FakeWindowsToastBridge()
        // Initialization triggers startup clear
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        assertTrue(fakeBridge.clearedGroups.contains("tr-remote"))
        val caps = adapter.capabilities()
        assertTrue(caps.alertsSupported)
        assertTrue(caps.actionsSupported)
        assertTrue(caps.notificationsEnabled)
    }

    @Test
    fun testTagDeterministicComputationAndLength() {
        val msgId = "11111111-2222-3333-4444-555555555555"
        val tag1 = WindowsRemoteNotificationAdapter.computeTag(msgId)
        val tag2 = WindowsRemoteNotificationAdapter.computeTag(msgId)

        assertEquals(tag1, tag2, "Tag computation must be deterministic")
        assertEquals(16, tag1.length, "Tag must be exactly 16 hex characters")
        assertTrue(tag1.all { it in '0'..'9' || it in 'a'..'f' }, "Tag must be lowercase hex")
    }

    @Test
    fun testTagCollisionResolutionAssignsDistinctTags() = runTest {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        val id1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val id2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

        val res1 = adapter.show(id1, 1L)
        val res2 = adapter.show(id2, 1L)

        assertTrue(res1 is RemotePostResult.Posted)
        assertTrue(res2 is RemotePostResult.Posted)

        assertEquals(2, fakeBridge.remoteDispatched.size)
        val tag1 = fakeBridge.remoteDispatched[0].tag
        val tag2 = fakeBridge.remoteDispatched[1].tag

        assertNotEquals(tag1, tag2, "Tags for distinct messages must not collide")
        assertEquals("tr-remote", fakeBridge.remoteDispatched[0].group)
        assertEquals("tr-remote", fakeBridge.remoteDispatched[1].group)
    }

    @Test
    fun testActivationCallbackWithUuidPayload() = runTest {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        var activatedMsgId: String? = null
        var activatedGen: Long? = null

        adapter.setActivationCallback { id, gen ->
            activatedMsgId = id
            activatedGen = gen
        }

        val msgUuid = "550e8400-e29b-41d4-a716-446655440000"
        adapter.show(msgUuid, 10L)

        // Native C++ Activated event passes the captured messageId UUID directly
        fakeBridge.simulateActivation(msgUuid, 10L)

        assertEquals(msgUuid, activatedMsgId, "Callback must deliver exact messageId UUID")
        assertEquals(10L, activatedGen)
    }

    @Test
    fun testStaleGenerationFencingInShowsAndCallbacks() = runTest {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        var callbackCount = 0
        adapter.setActivationCallback { _, _ ->
            callbackCount++
        }

        // 1. Post notification for generation 1
        val res1 = adapter.show("msg-gen1", 1L)
        assertTrue(res1 is RemotePostResult.Posted)

        // 2. Clear remote at generation 2 (account switch / generation advance)
        adapter.clearRemote(2L)

        // 3. Late arriving show for generation 1 must be rejected by generation fence
        val resStale = adapter.show("msg-late-gen1", 1L)
        assertTrue(resStale is RemotePostResult.Failed)
        assertTrue((resStale as RemotePostResult.Failed).reason.contains("Stale generation"))

        // 4. Late arriving callback for generation 1 must be discarded by generation fence
        fakeBridge.simulateActivation("msg-gen1", 1L)
        assertEquals(0, callbackCount, "Callbacks from older generations must be fenced")

        // 5. Shows and callbacks for current generation 3 succeed
        val res3 = adapter.show("msg-gen3", 3L)
        assertTrue(res3 is RemotePostResult.Posted)
        fakeBridge.simulateActivation("msg-gen3", 3L)
        assertEquals(1, callbackCount, "Callbacks for current generation must be delivered")
    }

    @Test
    fun testStaleCallbackFencedAfterClose() = runTest {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        var invoked = false
        adapter.setActivationCallback { _, _ ->
            invoked = true
        }

        adapter.show("msg-1", 1L)

        // Close adapter
        adapter.close()
        assertTrue(fakeBridge.destroyCount > 0)

        // Simulate click arriving after close
        fakeBridge.simulateActivation("msg-1", 1L)
        assertFalse(invoked, "Callbacks after close() must be fenced and discarded")

        // Show after close must fail
        val postAfterClose = adapter.show("msg-2", 2L)
        assertTrue(postAfterClose is RemotePostResult.Failed)
    }

    @Test
    fun testScopedClearOnlyClearsRemoteGroup() = runTest {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)
        fakeBridge.clearedGroups.clear()

        adapter.show("msg-1", 1L)
        adapter.clearRemote(1L)

        assertEquals(listOf("tr-remote"), fakeBridge.clearedGroups)
    }

    @Test
    fun testCapabilitiesReflectsOsSetting() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true, setting = WindowsNotificationSetting.DISABLED_FOR_USER)
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        val caps = adapter.capabilities()
        assertTrue(caps.alertsSupported)
        assertTrue(caps.actionsSupported)
        assertFalse(caps.notificationsEnabled)

        // Show should return PermissionDenied when disabled by OS
        val result = adapter.show("msg-1", 1L)
        assertTrue(result is RemotePostResult.PermissionDenied)
    }

    @Test
    fun testUnavailableBridgeHandling() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = false)
        val adapter = WindowsRemoteNotificationAdapter(fakeBridge)

        val caps = adapter.capabilities()
        assertFalse(caps.alertsSupported)
        assertFalse(caps.actionsSupported)
        assertFalse(caps.notificationsEnabled)

        val result = adapter.show("msg-1", 1L)
        assertTrue(result is RemotePostResult.Failed)
    }
}
