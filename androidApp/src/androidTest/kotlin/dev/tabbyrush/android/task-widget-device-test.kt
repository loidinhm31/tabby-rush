package dev.tabbyrush.android

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.SizeMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.SurfaceSnapshot
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.TaskSurfaceAccess
import dev.tabbyrush.shared.contract.Todo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Android device tests verifying Glance widget component instantiation,
 * responsive size configuration, PendingIntent generation, mock host integration,
 * and updater all-ID clearing behavior on actual Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class TaskWidgetDeviceTest {

    private lateinit var context: Context
    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 3L)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TabbyRushTaskWidget.clearState()
    }

    @Test
    fun testWidgetResponsiveSizeModeConfiguration() {
        val widget = TabbyRushTaskWidget()
        val sizeMode = widget.sizeMode
        assertTrue("Widget sizeMode must be Responsive", sizeMode is SizeMode.Responsive)

        val responsive = sizeMode as SizeMode.Responsive
        val sizes = responsive.sizes
        assertEquals(4, sizes.size)
        assertTrue(sizes.contains(DpSize(120.dp, 120.dp)))
        assertTrue(sizes.contains(DpSize(240.dp, 120.dp)))
        assertTrue(sizes.contains(DpSize(240.dp, 240.dp)))
        assertTrue(sizes.contains(DpSize(320.dp, 360.dp)))
    }

    @Test
    fun testReceiverDelegatesToTabbyRushTaskWidget() {
        val receiver = TabbyRushTaskWidgetReceiver()
        val widget = receiver.glanceAppWidget
        assertTrue("Receiver must host TabbyRushTaskWidget", widget is TabbyRushTaskWidget)
    }

    @Test
    fun testPendingIntentsCreation() {
        val openPending = createOpenTaskPendingIntent(context, testToken, "task-1")
        assertNotNull("Open task PendingIntent must not be null", openPending)

        val addPending = createAddTaskPendingIntent(context, testToken)
        assertNotNull("Add task PendingIntent must not be null", addPending)

        val appPending = createOpenAppPendingIntent(context)
        assertNotNull("Open app PendingIntent must not be null", appPending)
    }

    @Test
    fun testResourceStringsResolvable() {
        val title = context.getString(R.string.task_widget_title)
        val loading = context.getString(R.string.task_widget_loading)
        val signedOut = context.getString(R.string.task_widget_signed_out)
        val locked = context.getString(R.string.task_widget_locked)
        val unavailable = context.getString(R.string.task_widget_unavailable)
        val empty = context.getString(R.string.task_widget_empty)

        assertEquals("TabbyRush Tasks", title)
        assertTrue(loading.isNotBlank())
        assertTrue(signedOut.isNotBlank())
        assertTrue(locked.isNotBlank())
        assertTrue(unavailable.isNotBlank())
        assertTrue(empty.isNotBlank())
    }

    private class DeviceTestAccess(
        initialReadiness: SurfaceReadiness = SurfaceReadiness.Unavailable
    ) : TaskSurfaceAccess {
        private val _readiness = MutableStateFlow(initialReadiness)
        override val readiness: StateFlow<SurfaceReadiness> = _readiness.asStateFlow()

        var setCompletedCalled = false
        var lastCompletedTaskId: String? = null
        var lastCompletedValue: Boolean? = null
        var publishedCount = 0

        override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness = _readiness.value

        override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> {
            if (_readiness.value != SurfaceReadiness.Ready(token)) {
                return SurfaceResult.Rejected
            }
            return SurfaceResult.Value(
                SurfaceSnapshot(
                    token = token,
                    sections = TaskSections(
                        incomplete = listOf(
                            Todo(id = "task-100", title = "Device Test Task", completed = false, createdAt = 500L, updatedAt = 500L)
                        ),
                        completed = emptyList()
                    ),
                    schedules = emptyList()
                )
            )
        }

        override suspend fun setCompleted(
            token: SurfaceAccountToken,
            taskId: String,
            completed: Boolean,
            nowEpochMs: Long
        ): SurfaceResult<CompletionChange> {
            setCompletedCalled = true
            lastCompletedTaskId = taskId
            lastCompletedValue = completed
            return SurfaceResult.Value(CompletionChange.Changed)
        }

        override suspend fun publishIfCurrent(
            token: SurfaceAccountToken,
            publish: suspend () -> Unit
        ): Boolean {
            publishedCount++
            publish()
            return true
        }

        override suspend fun validateNavigation(token: SurfaceAccountToken, taskId: String?): Boolean = true
    }

    private class DeviceTestHost(
        override val access: TaskSurfaceAccess
    ) : AndroidTaskSurfaceHost {
        var refreshCount = 0

        override suspend fun refresh(reason: SurfaceRefreshReason) {
            refreshCount++
        }
    }

    @Test
    fun testUpdaterRefreshAndClearLifecycle() = runBlocking {
        val access = DeviceTestAccess(SurfaceReadiness.Ready(testToken))
        val host = DeviceTestHost(access)

        // 1. Refresh updates display state
        TaskWidgetUpdater.refreshAll(context, host)
        assertEquals(1, access.publishedCount)
        val stateAfterRefresh = TabbyRushTaskWidget.displayState.value
        assertTrue("Display state must be Content after refresh", stateAfterRefresh is WidgetDisplayState.Content)
        val content = stateAfterRefresh as WidgetDisplayState.Content
        assertEquals(testToken, content.token)
        assertEquals(1, content.sections.incomplete.size)
        assertEquals("Device Test Task", content.sections.incomplete.first().title)

        // 2. Clear all resets to SignedOut immediately
        TaskWidgetUpdater.clearAll(context)
        val stateAfterClear = TabbyRushTaskWidget.displayState.value
        assertEquals("Display state must be SignedOut after clearAll", WidgetDisplayState.SignedOut, stateAfterClear)

        // 3. Await quiescence completes cleanly
        TaskWidgetUpdater.awaitQuiescence(2_000L)
    }

    @Test
    fun testToggleTaskActionCallbackFailsClosedWithoutHost() = runBlocking {
        val params = TaskWidgetActionKeys.toggleTaskParameters(
            token = testToken,
            taskId = "task-42",
            desiredCompleted = true
        )

        // When application context is not yet TaskSurfaceHostOwner (pre-Phase 05),
        // callback fails closed safely without unhandled exceptions or crashes.
        val callback = ToggleTaskActionCallback()
        val fakeGlanceId = androidx.glance.appwidget.AppWidgetId(101)
        callback.onAction(context, fakeGlanceId, params)

        // Verifies fail-closed execution without exception
        assertTrue(true)
    }
}
