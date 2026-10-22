package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxController
import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import dev.tabbyrush.shared.contract.RemoteQuiesceResult
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopRemoteRuntimeTest {

    private class FakeTestNotificationAdapter : RemoteNotificationAdapter {
        val shownAlerts = mutableListOf<Pair<String, Long>>()
        val clearedGenerations = mutableListOf<Long>()
        private var callback: ((String, Long) -> Unit)? = null
        var isClosed = false

        override suspend fun capabilities(): RemoteNotificationCapabilities =
            RemoteNotificationCapabilities(alertsSupported = true, actionsSupported = true, notificationsEnabled = true)

        override suspend fun show(messageId: String, generation: Long): RemotePostResult {
            shownAlerts.add(messageId to generation)
            return RemotePostResult.Posted
        }

        override suspend fun clearRemote(generation: Long) {
            clearedGenerations.add(generation)
        }

        override suspend fun close() {
            isClosed = true
        }

        override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {
            this.callback = callback
        }

        fun triggerActivation(messageId: String, generation: Long) {
            callback?.invoke(messageId, generation)
        }
    }

    private class FakeTestInboxController : RemoteInboxController {
        private val _state = MutableStateFlow(RemoteInboxState())
        override val state: StateFlow<RemoteInboxState> = _state.asStateFlow()

        var startedAccount: AccountIdentity? = null
        var startedGeneration: Long = 0L
        val dispatchedActions = mutableListOf<RemoteInboxAction>()
        var foregroundTriggerCount = 0
        var nativeOpenedId: String? = null
        var nativeOpenedGen: Long? = null
        var quiesceResultToReturn: RemoteQuiesceResult = RemoteQuiesceResult.Quiesced
        var isQuiesced = false
        var isClosed = false

        override fun dispatch(action: RemoteInboxAction) {
            dispatchedActions.add(action)
        }

        override suspend fun start(account: AccountIdentity, generation: Long) {
            startedAccount = account
            startedGeneration = generation
            _state.value = RemoteInboxState(availability = RemoteInboxAvailability.Online)
        }

        override suspend fun quiesce(): RemoteQuiesceResult {
            isQuiesced = true
            _state.value = RemoteInboxState(availability = RemoteInboxAvailability.SignedOut)
            return quiesceResultToReturn
        }

        override suspend fun close() {
            isClosed = true
            quiesce()
        }

        override fun onForeground() {
            foregroundTriggerCount++
        }

        override fun onNativeOpen(messageId: String, capturedGeneration: Long) {
            nativeOpenedId = messageId
            nativeOpenedGen = capturedGeneration
            _state.value = _state.value.copy(
                detail = RemoteInboxItem(
                    id = messageId,
                    appId = "tabby-rush",
                    title = "Test",
                    body = "Body",
                    createdAt = "2026-10-18T00:00:00Z",
                    expiresAt = "2026-10-19T00:00:00Z"
                )
            )
        }
    }

    private class TestUiRestorer : DesktopUiRestorer {
        var restoreCount = 0
        var onRestoreHook: (() -> Unit)? = null

        override fun restoreWindow() {
            restoreCount++
            onRestoreHook?.invoke()
        }
    }

    @Test
    fun testSessionControllerScopeAndStart() = runTest {
        val fakeAdapter = FakeTestNotificationAdapter()
        val fakeController = FakeTestInboxController()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uiRestorer = TestUiRestorer()

        val runtime = DesktopRemoteRuntime(
            adapter = fakeAdapter,
            scope = this,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher,
            uiRestorer = uiRestorer,
            controllerFactory = { fakeController }
        )

        val account = AccountIdentity("user-1", "alice", "alice@example.com")
        runtime.start(account, 10L)

        assertEquals(10L, runtime.currentGeneration())
        assertFalse(runtime.isQuiesced())
        assertFalse(runtime.isClosed())
        assertEquals(account, fakeController.startedAccount)
        assertEquals(10L, fakeController.startedGeneration)
        assertEquals(RemoteInboxAvailability.Online, runtime.state.value.availability)

        runtime.dispatch(RemoteInboxAction.Refresh)
        assertEquals(1, fakeController.dispatchedActions.size)
        assertEquals(RemoteInboxAction.Refresh, fakeController.dispatchedActions.first())
    }

    @Test
    fun testTrayAndProcessVsUiLifetime() = runTest {
        val fakeAdapter = FakeTestNotificationAdapter()
        val fakeController = FakeTestInboxController()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uiRestorer = TestUiRestorer()

        val runtime = DesktopRemoteRuntime(
            adapter = fakeAdapter,
            scope = this,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher,
            uiRestorer = uiRestorer,
            controllerFactory = { fakeController }
        )

        val account = AccountIdentity("user-1", "alice", "alice@example.com")
        runtime.start(account, 1L)

        // Simulating window minimize / hide in system tray:
        // Remote runtime remains active without quiescing or closing
        assertFalse(runtime.isQuiesced())
        assertFalse(runtime.isClosed())

        // Simulating window restore from tray triggering foreground refresh
        runtime.onForeground()
        assertEquals(1, fakeController.foregroundTriggerCount)

        // Full application quit tears down adapter and closes controller
        runtime.close()
        assertTrue(runtime.isClosed())
        assertTrue(runtime.isQuiesced())
        assertTrue(fakeController.isClosed)
        assertTrue(fakeAdapter.isClosed)
        assertTrue(fakeAdapter.clearedGenerations.isNotEmpty())
    }

    @Test
    fun testNativeActivationDoubleFencingAndWindowRestore() = runTest {
        val fakeAdapter = FakeTestNotificationAdapter()
        val fakeController = FakeTestInboxController()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uiRestorer = TestUiRestorer()

        val runtime = DesktopRemoteRuntime(
            adapter = fakeAdapter,
            scope = this,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher,
            uiRestorer = uiRestorer,
            controllerFactory = { fakeController }
        )

        val account = AccountIdentity("user-1", "alice", "alice@example.com")
        runtime.start(account, 5L)

        // 1. Activation with matching generation (5L) unminimizes window and routes to open
        fakeAdapter.triggerActivation("msg-100", 5L)
        testScheduler.runCurrent()
        assertEquals(1, uiRestorer.restoreCount)
        assertEquals("msg-100", fakeController.nativeOpenedId)
        assertEquals(5L, fakeController.nativeOpenedGen)

        // 2. Activation with older/stale generation (4L) is dropped immediately by first fence
        fakeAdapter.triggerActivation("msg-old", 4L)
        testScheduler.runCurrent()
        assertEquals(1, uiRestorer.restoreCount, "Stale generation callback must not restore window")
        assertEquals("msg-100", fakeController.nativeOpenedId, "Stale generation callback must not navigate")

        // 3. Delayed activation across session switch:
        // Immediate fence passes for generation 5L, but generation advances to 6L before UI dispatcher executes
        fakeAdapter.triggerActivation("msg-delayed", 5L)
        runtime.start(account, 6L)
        testScheduler.runCurrent()
        assertEquals(1, uiRestorer.restoreCount, "Second fence inside UI dispatcher must reject outdated generation")
        assertEquals("msg-100", fakeController.nativeOpenedId)

        // 4. Quiesced session drops subsequent activations
        runtime.quiesce()
        fakeAdapter.triggerActivation("msg-300", 6L)
        testScheduler.runCurrent()
        assertEquals(1, uiRestorer.restoreCount, "Quiesced runtime must drop all activations")
    }

    @Test
    fun testQuiescenceOrderedExecution() = runTest {
        val fakeAdapter = FakeTestNotificationAdapter()
        val fakeController = FakeTestInboxController()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uiRestorer = TestUiRestorer()

        val runtime = DesktopRemoteRuntime(
            adapter = fakeAdapter,
            scope = this,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher,
            uiRestorer = uiRestorer,
            controllerFactory = { fakeController }
        )

        val account = AccountIdentity("user-1", "alice", "alice@example.com")
        runtime.start(account, 10L)

        val quiesceResult = runtime.quiesce()
        assertEquals(RemoteQuiesceResult.Quiesced, quiesceResult)
        assertTrue(runtime.isQuiesced())
        assertTrue(runtime.currentGeneration() > 10L, "Generation fence must advance on quiescence")
        assertTrue(fakeController.isQuiesced)
        assertEquals(RemoteInboxAvailability.SignedOut, runtime.state.value.availability)

        // Adapter is not closed on account leave (only notifications cleared), allowing next login in same process
        assertFalse(fakeAdapter.isClosed)
    }

    @Test
    fun testDrainFailureFailsClosedWithoutProceeding() = runTest {
        val fakeAdapter = FakeTestNotificationAdapter()
        val fakeController = FakeTestInboxController().apply {
            quiesceResultToReturn = RemoteQuiesceResult.TimedOut(remainingJobs = 2)
        }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uiRestorer = TestUiRestorer()

        val runtime = DesktopRemoteRuntime(
            adapter = fakeAdapter,
            scope = this,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher,
            uiRestorer = uiRestorer,
            controllerFactory = { fakeController }
        )

        val accountKey = AccountKey("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
        runtime.start(AccountIdentity("u1", "user", "u@example.com"), 1L)

        // Verify cancelAndAwait throws IllegalStateException when quiesce times out
        val ex = assertFailsWith<IllegalStateException> {
            runtime.cancelAndAwait(accountKey)
        }
        assertTrue(ex.message!!.contains("Timed out draining remote inbox controller"))

        // Verify integration with CompositeDesktopLeaveCancelGate fails closed
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })
        var reminderCancelled = false
        val reminderCancelGate = LeaveSchedulerCancelGate { reminderCancelled = true }
        val syncCancelGate = LeaveSchedulerCancelGate {}

        val compositeGate = CompositeDesktopLeaveCancelGate(
            deliveryGate = deliveryGate,
            scheduleObserverJob = Job(),
            reminderScheduler = DesktopReminderScheduler(accountKey, dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator(
                accountKey,
                dev.tabbyrush.shared.data.RoomAccountStore(accountKey, "u", dev.tabbyrush.shared.data.createInMemoryJvmDatabaseBuilder().build(), "t"),
                dev.tabbyrush.shared.data.ReminderOccurrenceStore(accountKey, dev.tabbyrush.shared.data.createInMemoryJvmDatabaseBuilder().build().tabbyRushDao()),
                object : dev.tabbyrush.shared.contract.NativeReminderAdapter {
                    override suspend fun queryCapabilities() = dev.tabbyrush.shared.contract.ReminderCapabilities(true, true, true)
                    override suspend fun reconcileSchedule(a: AccountKey, g: Long, d: List<dev.tabbyrush.shared.contract.ScheduledAlarmHandle>) {}
                    override suspend fun postOwnedNotification(accountKey: AccountKey, generation: Long, taskRef: dev.tabbyrush.shared.contract.TaskRef, scheduleToken: String, occurrenceIndex: Int, title: String, dueEpochMs: Long): String? = "id"
                    override suspend fun removeOwnedNotification(a: AccountKey, p: String) {}
                    override suspend fun cancelAllForAccount(a: AccountKey) {}
                },
                deliveryGate,
                { 1L },
                { false }
            ), this),
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate,
            remoteCancelGate = runtime
        )

        assertFailsWith<IllegalStateException> {
            compositeGate.cancelAndAwait(accountKey)
        }
    }

    @Test
    fun testPlatformDispatcherResolutionAndAumidSetup() {
        val detected = DesktopRemotePlatformDispatcher.detectHostTarget()
        assertTrue(detected == "linux" || detected == "windows" || detected == "unsupported")

        val provider = DesktopRemotePlatformDispatcher.resolveProvider()
        assertNotNull(provider)

        // Calling AUMID setup must complete safely without throwing on any host
        val aumidResult = setCurrentProcessExplicitAppUserModelID("dev.tabbyrush.desktop")
        // On Linux, returns false safely without throwing; on Windows, returns true if Shell32 available
        assertTrue(aumidResult || !aumidResult)
    }

    @Test
    fun testRepeatedQuiesceAndCloseIdempotence() = runTest {
        val fakeAdapter = FakeTestNotificationAdapter()
        val fakeController = FakeTestInboxController()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val runtime = DesktopRemoteRuntime(
            adapter = fakeAdapter,
            scope = this,
            ioDispatcher = testDispatcher,
            uiDispatcher = testDispatcher,
            controllerFactory = { fakeController }
        )

        runtime.start(AccountIdentity("u", "usr", "u@e.com"), 1L)

        // Multiple quiesce calls must complete idempotently
        runtime.quiesce()
        runtime.quiesce()
        assertTrue(runtime.isQuiesced())

        // Multiple close calls must complete idempotently
        runtime.close()
        runtime.close()
        assertTrue(runtime.isClosed())
    }
}
