package dev.tabbyrush.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.DeadlineSelectionKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.TaskDeadline
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskSurfacePreferencesDeviceTest {

    private lateinit var context: Context
    private lateinit var preferences: TaskSurfacePreferences
    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 10L)

    private fun createTestKey(taskId: String, date: String = "2026-10-16", time: String? = "15:00"): DeadlineSelectionKey {
        return DeadlineSelectionKey(
            token = testToken,
            taskId = taskId,
            deadline = TaskDeadline(
                date = date,
                time = time,
                zone = "UTC",
                offsetSeconds = 0L,
                anchorEpochMs = 1792162800000L,
                overdueEpochMs = 1792162800000L
            )
        )
    }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        preferences = TaskSurfacePreferences(context)
        preferences.clear(testAccountKey)
    }

    @After
    fun tearDown() = runBlocking {
        preferences.clear(testAccountKey)
    }

    @Test
    fun testInitialStateDefaultOff() = runBlocking {
        val state = preferences.read(testAccountKey)
        assertFalse("Default state must be disabled", state.enabled)
        assertNull(state.suppressed)
        assertNull(state.postedKey)
        assertNull(state.postedReceipt)
    }

    @Test
    fun testSetEnabledPersistence() = runBlocking {
        preferences.setEnabled(testToken, true)
        var state = preferences.read(testAccountKey)
        assertTrue("State must reflect enabled", state.enabled)

        preferences.setEnabled(testToken, false)
        state = preferences.read(testAccountKey)
        assertFalse("State must reflect disabled", state.enabled)
    }

    @Test
    fun testSetEnabledClearsSuppression() = runBlocking {
        val key = createTestKey("t1")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key, "receipt-xyz")
        val suppressed = preferences.suppressIfCurrent(key, "receipt-xyz")
        assertTrue(suppressed)

        var state = preferences.read(testAccountKey)
        assertEquals(key, state.suppressed)

        // Toggling to false clears suppression
        preferences.setEnabled(testToken, false)
        state = preferences.read(testAccountKey)
        assertNull("Suppression must be cleared on disable", state.suppressed)

        // Re-enabling after being false also stays cleared
        preferences.setEnabled(testToken, true)
        state = preferences.read(testAccountKey)
        assertNull(state.suppressed)
    }

    @Test
    fun testReconcileSelectionClearsOnDisappearance() = runBlocking {
        val key = createTestKey("t1")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key, "receipt-abc")
        preferences.suppressIfCurrent(key, "receipt-abc")

        var state = preferences.read(testAccountKey)
        assertEquals(key, state.suppressed)

        // When selection disappears (key == null), suppression and post state are cleared
        state = preferences.reconcileSelection(testToken, null)
        assertNull("Suppression must clear when selection disappears", state.suppressed)
        assertNull(state.postedKey)
        assertNull(state.postedReceipt)
    }

    @Test
    fun testReconcileSelectionClearsSuppressionOnKeyChange() = runBlocking {
        val key1 = createTestKey("t1")
        val key2 = createTestKey("t2")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key1, "receipt-1")
        preferences.suppressIfCurrent(key1, "receipt-1")

        // Reconcile with different key (key2)
        val state = preferences.reconcileSelection(testToken, key2)
        assertNull("Suppression must clear when selection changes to another key", state.suppressed)
    }

    @Test
    fun testRecordPostAndSuppressIfCurrent() = runBlocking {
        val key = createTestKey("t1")
        preferences.setEnabled(testToken, true)

        preferences.recordPost(key, "r-42")
        var state = preferences.read(testAccountKey)
        assertEquals(key, state.postedKey)
        assertEquals("r-42", state.postedReceipt)
        assertNull(state.suppressed)

        val success = preferences.suppressIfCurrent(key, "r-42")
        assertTrue(success)

        state = preferences.read(testAccountKey)
        assertEquals(key, state.suppressed)
        assertNull("Active post key must be cleared on suppression", state.postedKey)
        assertNull("Active post receipt must be cleared on suppression", state.postedReceipt)
    }

    @Test
    fun testSuppressIfCurrentRejectsStaleReceipt() = runBlocking {
        val key = createTestKey("t1")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key, "active-receipt")

        val success = preferences.suppressIfCurrent(key, "stale-receipt")
        assertFalse("Stale receipt must be rejected", success)

        val state = preferences.read(testAccountKey)
        assertNull("Suppression must not be set on rejected receipt", state.suppressed)
        assertEquals(key, state.postedKey)
        assertEquals("active-receipt", state.postedReceipt)
    }

    @Test
    fun testInvalidatePostPreventsDelayedDismissal() = runBlocking {
        val key = createTestKey("t1")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key, "receipt-to-invalidate")

        // Programmatic removal calls invalidatePost
        preferences.invalidatePost(testAccountKey)

        val state = preferences.read(testAccountKey)
        assertNull("Invalidate post must clear posted key", state.postedKey)
        assertNull("Invalidate post must clear posted receipt", state.postedReceipt)

        // Delayed dismissal with old receipt fails
        val success = preferences.suppressIfCurrent(key, "receipt-to-invalidate")
        assertFalse(success)
    }

    @Test
    fun testReopenInstancePreservesDurableState() = runBlocking {
        val key = createTestKey("t-reopen")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key, "r-persistent")
        preferences.suppressIfCurrent(key, "r-persistent")

        // Instantiate completely fresh instance mimicking process restart
        val freshPreferences = TaskSurfacePreferences(context)
        val state = freshPreferences.read(testAccountKey)

        assertTrue(state.enabled)
        assertEquals(key, state.suppressed)
        assertNull(state.postedKey)
        assertNull(state.postedReceipt)
    }

    @Test
    fun testClearRemovesAllAccountData() = runBlocking {
        val key = createTestKey("t-clear")
        preferences.setEnabled(testToken, true)
        preferences.recordPost(key, "r-clear")

        preferences.clear(testAccountKey)
        val state = preferences.read(testAccountKey)

        assertFalse(state.enabled)
        assertNull(state.suppressed)
        assertNull(state.postedKey)
        assertNull(state.postedReceipt)
    }

    @Test
    fun testDataUriEncodingWithDelimiterBearingTaskIds() {
        val complexTaskId = "task/with?delimiters#and spaces"
        val complexReceipt = "receipt/alpha#123"

        val completeUri = TaskSurfaceIntents.buildCompleteDataUri(testToken, complexTaskId)
        assertEquals("tabbyrush", completeUri.scheme)
        assertEquals("task-surface", completeUri.authority)
        val completeSegments = completeUri.pathSegments
        assertEquals("complete", completeSegments[0])
        assertEquals(testAccountKey.value, completeSegments[1])
        assertEquals("10", completeSegments[2])
        assertEquals(complexTaskId, completeSegments[3])

        val openUri = TaskSurfaceIntents.buildOpenDataUri(testToken, complexTaskId)
        val openSegments = openUri.pathSegments
        assertEquals("open", openSegments[0])
        assertEquals(complexTaskId, openSegments[3])

        val dismissUri = TaskSurfaceIntents.buildDismissDataUri(testToken, complexTaskId, complexReceipt)
        val dismissSegments = dismissUri.pathSegments
        assertEquals("dismiss", dismissSegments[0])
        assertEquals(complexTaskId, dismissSegments[3])
        assertEquals(complexReceipt, dismissSegments[4])
    }
}
