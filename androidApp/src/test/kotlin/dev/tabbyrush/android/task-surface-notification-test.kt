package dev.tabbyrush.android

import android.content.Intent
import android.net.Uri
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.DeadlineSelectionKey
import dev.tabbyrush.shared.contract.PersistedTaskSchedule
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.selectNextDeadline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskSurfaceNotificationTest {

    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 42L)

    @Test
    fun testDeadlineSelectionKeyCodecRoundTripTimedDeadline() {
        val deadline = TaskDeadline(
            date = "2026-10-16",
            time = "14:30",
            zone = "America/New_York",
            offsetSeconds = -14400L,
            anchorEpochMs = 1792161000000L,
            overdueEpochMs = 1792161000000L
        )
        val taskRef = TaskRef("alice", "task-timed-1")
        val key = DeadlineSelectionKey(
            token = testToken,
            taskRef = taskRef,
            deadline = deadline
        )
        val encoded = DeadlineSelectionKeyCodec.encode(key)
        assertTrue(encoded.startsWith("v1:"), "Encoded key must contain version prefix")
        assertFalse(encoded.contains("secret-task-title"), "Codec must never leak task titles")

        val decoded = DeadlineSelectionKeyCodec.decode(encoded)
        assertNotNull(decoded, "Decoded key must not be null")
        assertEquals(key.token, decoded.token)
        assertEquals(key.taskRef, decoded.taskRef)
        assertEquals(key.taskId, decoded.taskId)
        assertEquals(key.ownerUserId, decoded.ownerUserId)
        assertEquals(key.deadline.time, decoded.deadline.time)
        assertEquals(key.deadline.zone, decoded.deadline.zone)
        assertEquals(key.deadline.offsetSeconds, decoded.deadline.offsetSeconds)
        assertEquals(key.deadline.reminderAnchorTime, decoded.deadline.reminderAnchorTime)
        assertEquals(key.deadline.anchorEpochMs, decoded.deadline.anchorEpochMs)
        assertEquals(key.deadline.overdueEpochMs, decoded.deadline.overdueEpochMs)
        assertEquals(key, decoded)
    }

    @Test
    fun testDeadlineSelectionKeyCodecRoundTripDateOnlyDeadline() {
        val deadline = TaskDeadline(
            date = "2026-10-17",
            time = null,
            zone = "UTC",
            offsetSeconds = 0L,
            reminderAnchorTime = "09:00",
            anchorEpochMs = 1792227600000L,
            overdueEpochMs = 1792281600000L
        )
        val taskRef = TaskRef("alice", "task-dateonly-1")
        val key = DeadlineSelectionKey(
            token = testToken,
            taskRef = taskRef,
            deadline = deadline
        )
        val encoded = DeadlineSelectionKeyCodec.encode(key)
        val decoded = DeadlineSelectionKeyCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(key, decoded)
        assertNull(decoded.deadline.time)
        assertEquals("09:00", decoded.deadline.reminderAnchorTime)
    }

    @Test
    fun testDeadlineSelectionKeyCodecRejectsMalformedData() {
        assertNull(DeadlineSelectionKeyCodec.decode(null))
        assertNull(DeadlineSelectionKeyCodec.decode(""))
        assertNull(DeadlineSelectionKeyCodec.decode("   "))
        assertNull(DeadlineSelectionKeyCodec.decode("not-versioned:acc=123"))
        assertNull(DeadlineSelectionKeyCodec.decode("v1:acc=123")) // missing required fields
        assertNull(DeadlineSelectionKeyCodec.decode("v1:acc=123&gen=not-a-number&tid=t1"))
        assertNull(DeadlineSelectionKeyCodec.decode("v1:acc=123&gen=1&tid=t1&date=2026-10-16&zone=UTC&offset=0&anchorEpoch=1000&overdueEpoch=1000"), "Owner-less legacy key must be rejected")
    }

    @Test
    fun testTaskSurfaceIntentsRequestCodes() {
        val ref1 = TaskRef("alice", "task1")
        val ref2 = TaskRef("alice", "task2")
        val refBobSameRow = TaskRef("bob", "task1")

        val code1 = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 42L, ref1)
        val code2 = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 42L, ref2)
        val code3 = TaskSurfaceIntents.getRequestCode("open", testAccountKey, 42L, ref1)
        val code4 = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 43L, ref1)
        val codeBob = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 42L, refBobSameRow)

        assertTrue(code1 >= 0, "Request code must be non-negative")
        assertNotEquals(code1, code2, "Different task IDs must yield different request codes")
        assertNotEquals(code1, codeBob, "Different owners with colliding row IDs must yield different request codes")
        assertNotEquals(code1, code3, "Different operations must yield different request codes")
        assertNotEquals(code1, code4, "Different generations must yield different request codes")
    }

    @Test
    fun testIntentConstantsAndRequestCodeDeterminism() {
        assertEquals("tabbyrush_task_surface_v2", TaskSurfaceIntents.CHANNEL_ID)
        assertEquals("tabbyrush.task.surface", TaskSurfaceIntents.NOTIFICATION_TAG)
        assertEquals(4101, TaskSurfaceIntents.NOTIFICATION_ID)
        assertEquals("dev.tabbyrush.android.TASK_SURFACE", TaskSurfaceIntents.GROUP_KEY)
        val ref1 = TaskRef("alice", "t1")
        val ref2 = TaskRef("alice", "t2")
        val code1 = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 1L, ref1)
        val code1Repeat = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 1L, ref1)
        val code2 = TaskSurfaceIntents.getRequestCode("open", testAccountKey, 1L, ref1)
        val code3 = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 1L, ref2)
        val code4 = TaskSurfaceIntents.getRequestCode("dismiss", testAccountKey, 1L, ref1, "receipt-1")
        val code5 = TaskSurfaceIntents.getRequestCode("dismiss", testAccountKey, 1L, ref1, "receipt-2")
        assertEquals(code1, code1Repeat)
        assertTrue(code1 >= 0)
        assertTrue(code2 >= 0)
        assertTrue(code3 >= 0)
        assertTrue(code4 >= 0)
        assertTrue(code5 >= 0)
        assertTrue(code1 != code2, "Different operations must produce different request codes")
        assertTrue(code1 != code3, "Different tasks must produce different request codes")
        assertTrue(code4 != code5, "Different receipts must produce different request codes")
    }

    @Test
    fun testSelectionLogicEligibilityAndConflictKnowledge() {
        val dlConflict = TaskDeadline(
            date = "2026-10-16",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1_000_000L,
            overdueEpochMs = 1_000_000L
        )
        val dlNormal = TaskDeadline(
            date = "2026-10-16",
            time = "12:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 2_000_000L,
            overdueEpochMs = 2_000_000L
        )

        val scheduleConflict = PersistedTaskSchedule(
            taskRef = TaskRef("alice", "task-conflict"),
            title = "Conflict Task",
            completed = false,
            isDeleted = false,
            deadline = dlConflict,
            reminderRule = ReminderRule(enabled = false), // Reminder disabled!
            scheduleToken = "tok-1",
            hasUnresolvedConflict = true
        )
        val scheduleNormal = PersistedTaskSchedule(
            taskRef = TaskRef("alice", "task-normal"),
            title = "Normal Task",
            completed = false,
            isDeleted = false,
            deadline = dlNormal,
            reminderRule = ReminderRule(enabled = true),
            scheduleToken = "tok-2",
            hasUnresolvedConflict = false
        )
        // At now = 500_000L, both upcoming. dlConflict is due earlier (1_000_000L < 2_000_000L)
        val selection = selectNextDeadline(listOf(scheduleConflict, scheduleNormal), nowEpochMs = 500_000L)
        assertNotNull(selection)
        assertEquals("task-conflict", selection.taskId)
        assertEquals("Conflict Task", selection.title)
        assertTrue(selection.hasUnresolvedConflict, "Surface selection must preserve conflict knowledge")
    }

    @Test
    fun testSelectionIgnoresCompletedAndDeleted() {
        val dl = TaskDeadline(
            date = "2026-10-16",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1_000_000L,
            overdueEpochMs = 1_000_000L
        )
        val completedSchedule = PersistedTaskSchedule(
            taskRef = TaskRef("alice", "t-comp"),
            title = "Completed",
            completed = true,
            isDeleted = false,
            deadline = dl,
            reminderRule = ReminderRule(enabled = false),
            scheduleToken = "tok-c",
            hasUnresolvedConflict = false
        )
        val deletedSchedule = PersistedTaskSchedule(
            taskRef = TaskRef("alice", "t-del"),
            title = "Deleted",
            completed = false,
            isDeleted = true,
            deadline = dl,
            reminderRule = ReminderRule(enabled = false),
            scheduleToken = "tok-d",
            hasUnresolvedConflict = false
        )
        val selection = selectNextDeadline(listOf(completedSchedule, deletedSchedule), nowEpochMs = 500_000L)
        assertNull(selection, "Completed or deleted tasks must never be selected")
    }

    @Test
    fun testTimeReceiverSupportedActions() {
        assertTrue(Intent.ACTION_TIME_CHANGED in TaskSurfaceTimeReceiver.SUPPORTED_ACTIONS)
        assertTrue(Intent.ACTION_TIMEZONE_CHANGED in TaskSurfaceTimeReceiver.SUPPORTED_ACTIONS)
        assertTrue(Intent.ACTION_DATE_CHANGED in TaskSurfaceTimeReceiver.SUPPORTED_ACTIONS)
        assertEquals(3, TaskSurfaceTimeReceiver.SUPPORTED_ACTIONS.size)
        assertEquals(500L, TaskSurfaceTimeReceiver.DEBOUNCE_WINDOW_MS)
    }

    @Test
    fun testRefreshWorkerInvariants() {
        assertEquals(15 * 60 * 1000L, TaskSurfaceRefreshWorker.MIN_DELAY_MS)
        val uniqueName = TaskSurfaceRefreshWorker.getUniqueWorkName(testAccountKey)
        val tag = TaskSurfaceRefreshWorker.getAccountTag(testAccountKey)
        assertTrue(uniqueName.contains(testAccountKey.value))
        assertTrue(tag.contains(testAccountKey.value))
    }

    @Test
    fun testTaskSurfaceAvailabilityHierarchy() {
        val state = TaskSurfaceAvailability.OptedOut
        assertEquals("OptedOut", state.name)
        assertTrue(TaskSurfaceAvailability.values().contains(TaskSurfaceAvailability.PermissionDenied))
        assertTrue(TaskSurfaceAvailability.values().contains(TaskSurfaceAvailability.AppDisabled))
        assertTrue(TaskSurfaceAvailability.values().contains(TaskSurfaceAvailability.ChannelDisabled))
        assertTrue(TaskSurfaceAvailability.values().contains(TaskSurfaceAvailability.Available))
    }
}
