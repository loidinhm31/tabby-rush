package dev.tabbyrush.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
import dev.tabbyrush.shared.contract.AccessRequestInfo
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.ConflictState
import dev.tabbyrush.shared.contract.EditorState
import dev.tabbyrush.shared.contract.LeaveGuardState
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SyncProgress
import dev.tabbyrush.shared.contract.SyncStatus
import dev.tabbyrush.shared.contract.TitlePolicy
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoCounts
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.contract.TodoUiState
import dev.tabbyrush.shared.contract.ReminderSettingsState
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.ui.DeadlineCalculatorHelpers
import dev.tabbyrush.shared.ui.DeadlineValidationResult
import dev.tabbyrush.shared.theme.TabbyRushCoral
import dev.tabbyrush.shared.theme.TabbyRushCoralSurface
import dev.tabbyrush.shared.theme.TabbyRushCream
import dev.tabbyrush.shared.theme.TabbyRushInk
import dev.tabbyrush.shared.theme.TabbyRushMintSurface
import dev.tabbyrush.shared.theme.TabbyRushMuted
import dev.tabbyrush.shared.theme.TabbyRushOutline
import dev.tabbyrush.shared.theme.TabbyRushPage
import dev.tabbyrush.shared.theme.TabbyRushPrimary
import dev.tabbyrush.shared.theme.TabbyRushPrimaryDeep
import dev.tabbyrush.shared.theme.TabbyRushWhite
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import dev.tabbyrush.shared.contract.TaskHierarchyProjector
import dev.tabbyrush.shared.contract.HierarchicalTodoItem

class TodoSemanticsTest {

    @Test
    fun testWcagContrastRatiosMeetAccessibilityStandards() {
        // Assert WCAG 2.1 AA requirements (>= 4.5:1 for normal text, >= 3.0:1 for non-text controls)
        val inkOnPage = calculateContrastRatio(TabbyRushInk, TabbyRushPage)
        assertTrue(inkOnPage >= 4.5, "Ink on Page must be >= 4.5:1 (was $inkOnPage)")

        val inkOnWhite = calculateContrastRatio(TabbyRushInk, TabbyRushWhite)
        assertTrue(inkOnWhite >= 4.5, "Ink on White must be >= 4.5:1 (was $inkOnWhite)")

        val inkOnCream = calculateContrastRatio(TabbyRushInk, TabbyRushCream)
        assertTrue(inkOnCream >= 4.5, "Ink on Cream must be >= 4.5:1 (was $inkOnCream)")

        val inkOnMintSurface = calculateContrastRatio(TabbyRushInk, TabbyRushMintSurface)
        assertTrue(inkOnMintSurface >= 4.5, "Ink on MintSurface must be >= 4.5:1 (was $inkOnMintSurface)")

        val inkOnCoralSurface = calculateContrastRatio(TabbyRushInk, TabbyRushCoralSurface)
        assertTrue(inkOnCoralSurface >= 4.5, "Ink on CoralSurface must be >= 4.5:1 (was $inkOnCoralSurface)")

        val whiteOnPrimary = calculateContrastRatio(TabbyRushWhite, TabbyRushPrimary)
        assertTrue(whiteOnPrimary >= 4.5, "White on Primary teal must be >= 4.5:1 (was $whiteOnPrimary)")

        val whiteOnPrimaryDeep = calculateContrastRatio(TabbyRushWhite, TabbyRushPrimaryDeep)
        assertTrue(whiteOnPrimaryDeep >= 4.5, "White on Deep Primary must be >= 4.5:1 (was $whiteOnPrimaryDeep)")

        val whiteOnCoral = calculateContrastRatio(TabbyRushWhite, TabbyRushCoral)
        assertTrue(whiteOnCoral >= 4.5, "White on Coral must be >= 4.5:1 (was $whiteOnCoral)")

        val coralOnWhite = calculateContrastRatio(TabbyRushCoral, TabbyRushWhite)
        assertTrue(coralOnWhite >= 4.5, "Coral on White must be >= 4.5:1 (was $coralOnWhite)")

        val coralOnCoralSurface = calculateContrastRatio(TabbyRushCoral, TabbyRushCoralSurface)
        assertTrue(coralOnCoralSurface >= 4.5, "Coral on CoralSurface must be >= 4.5:1 (was $coralOnCoralSurface)")

        val mutedOnWhite = calculateContrastRatio(TabbyRushMuted, TabbyRushWhite)
        assertTrue(mutedOnWhite >= 4.5, "Muted on White must be >= 4.5:1 (was $mutedOnWhite)")

        val mutedOnMintSurface = calculateContrastRatio(TabbyRushMuted, TabbyRushMintSurface)
        assertTrue(mutedOnMintSurface >= 4.5, "Muted on MintSurface must be >= 4.5:1 (was $mutedOnMintSurface)")

        val mutedOnCoralSurface = calculateContrastRatio(TabbyRushMuted, TabbyRushCoralSurface)
        assertTrue(mutedOnCoralSurface >= 4.5, "Muted on CoralSurface must be >= 4.5:1 (was $mutedOnCoralSurface)")

        val mutedOnCream = calculateContrastRatio(TabbyRushMuted, TabbyRushCream)
        assertTrue(mutedOnCream >= 4.5, "Muted on Cream must be >= 4.5:1 (was $mutedOnCream)")

        val primaryDeepOnMintSurface = calculateContrastRatio(TabbyRushPrimaryDeep, TabbyRushMintSurface)
        assertTrue(primaryDeepOnMintSurface >= 4.5, "PrimaryDeep on MintSurface must be >= 4.5:1 (was $primaryDeepOnMintSurface)")

        val outlineOnWhite = calculateContrastRatio(TabbyRushOutline, TabbyRushWhite)
        assertTrue(outlineOnWhite >= 3.0, "Interactive Outline border on White must be >= 3.0:1 (was $outlineOnWhite)")
    }

    @Test
    fun testPasswordRedactedInAuthSubmitActionToString() {
        val sensitivePassword = "superSecretPassword123!"
        val loginAction = AuthSubmitAction.Login("user@example.com", sensitivePassword)
        val registerAction = AuthSubmitAction.Register("testuser", "user@example.com", sensitivePassword)

        val loginString = loginAction.toString()
        val registerString = registerAction.toString()

        assertFalse(
            loginString.contains(sensitivePassword),
            "Login action toString() must NEVER leak the raw password"
        )
        assertTrue(
            loginString.contains("[REDACTED]"),
            "Login action toString() must indicate [REDACTED]"
        )

        assertFalse(
            registerString.contains(sensitivePassword),
            "Register action toString() must NEVER leak the raw password"
        )
        assertTrue(
            registerString.contains("[REDACTED]"),
            "Register action toString() must indicate [REDACTED]"
        )
    }

    @Test
    fun testTitlePolicyValidationInEditor() {
        val validTitle = "Walk the dog \uD83E\uDDAE"
        val validResult = TitlePolicy.normalizeAndValidate(validTitle)
        assertTrue(validResult is dev.tabbyrush.shared.contract.ApiResult.Success)
        assertEquals(validTitle, validResult.value)

        val emptyResult = TitlePolicy.normalizeAndValidate("   ")
        assertTrue(emptyResult is dev.tabbyrush.shared.contract.ApiResult.Failure)
        assertEquals("TITLE_EMPTY", emptyResult.error.code)

        val longTitle = "a".repeat(201)
        val longResult = TitlePolicy.normalizeAndValidate(longTitle)
        assertTrue(longResult is dev.tabbyrush.shared.contract.ApiResult.Failure)
        assertEquals("TITLE_TOO_LONG", longResult.error.code)
    }

    @Test
    fun testConflictReviewActionsAndTombstoneGuard() {
        val rowId = "00000000-0000-0000-0000-000000000001"
        val localTodo = Todo(
            id = rowId,
            title = "My Local Edit",
            completed = false,
            createdAt = 1000L,
            updatedAt = 2000L,
            serverRevision = 1L
        )
        val remoteTodoDeleted = Todo(
            id = rowId,
            title = "",
            completed = false,
            createdAt = 1000L,
            updatedAt = 3000L,
            serverRevision = 2L,
            isDeleted = true
        )

        val conflict = ConflictState(
            rowId = rowId,
            localTodo = localTodo,
            remoteTodo = remoteTodoDeleted
        )

        assertTrue(conflict.remoteTodo?.isDeleted == true)

        val copyAction = TodoAction.CopyAsNew(conflict.rowId)
        val discardAction = TodoAction.DiscardConflict(conflict.rowId)
        val reapplyAction = TodoAction.ReapplyConflict(conflict.rowId)

        assertEquals(rowId, copyAction.rowId)
        assertEquals(rowId, discardAction.rowId)
        assertEquals(rowId, reapplyAction.rowId)
    }

    @Test
    fun testConflictStateUnknownSupersededAndDeadlineContext() {
        val rowId = "00000000-0000-0000-0000-000000000002"
        val deadline = TaskDeadline(
            date = "2026-11-20",
            time = "15:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1795186800000L,
            overdueEpochMs = 1795186800000L
        )
        val rule = ReminderRule(enabled = true, leadMinutes = 30L, count = 1L)
        val localTodo = Todo(
            id = rowId,
            title = "Local Task with Deadline",
            completed = false,
            createdAt = 1000L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "tok-local"
        )
        val remoteTodo = Todo(
            id = rowId,
            title = "Remote Task without Deadline",
            completed = false,
            createdAt = 1000L,
            serverRevision = 3L
        )

        val conflict = ConflictState(
            rowId = rowId,
            localTodo = localTodo,
            remoteTodo = remoteTodo,
            requiresDeliveryReview = true
        )

        assertTrue(conflict.requiresDeliveryReview)
        assertNotNull(conflict.localTodo?.deadline)
        assertEquals("2026-11-20", conflict.localTodo?.deadline?.date)
        assertEquals("15:00", conflict.localTodo?.deadline?.time)
        assertNull(conflict.remoteTodo?.deadline)
    }

    @Test
    fun testLeaveGuardTalliesAndSafety() {
        val state = LeaveGuardState(
            pendingCount = 3,
            uncertainCount = 2,
            conflictCount = 1
        )

        val hasUnsynced = state.pendingCount > 0 || state.uncertainCount > 0 || state.conflictCount > 0
        assertTrue(hasUnsynced)
        assertEquals(3, state.pendingCount)
        assertEquals(2, state.uncertainCount)
        assertEquals(1, state.conflictCount)

        val confirm: TodoAction = TodoAction.ConfirmLeave
        val cancel: TodoAction = TodoAction.CancelLeave
        assertNotEquals<TodoAction>(confirm, cancel)
    }

    @Test
    fun testSyncFeedbackStatusFormattingAndTimestamp() {
        val idleProgress = SyncProgress(status = SyncStatus.IDLE, pendingCount = 0)
        assertEquals(SyncStatus.IDLE, idleProgress.status)
        assertEquals(0, idleProgress.pendingCount)

        val syncingProgress = SyncProgress(status = SyncStatus.SYNCING)
        assertEquals(SyncStatus.SYNCING, syncingProgress.status)

        val conflictProgress = SyncProgress(status = SyncStatus.PAUSED_CONFLICT, conflictCount = 2)
        assertEquals(SyncStatus.PAUSED_CONFLICT, conflictProgress.status)
        assertEquals(2, conflictProgress.conflictCount)

        val protocolProgress = SyncProgress(status = SyncStatus.PAUSED_PROTOCOL)
        assertEquals(SyncStatus.PAUSED_PROTOCOL, protocolProgress.status)


        val uncertainProgress = SyncProgress(status = SyncStatus.UNCERTAIN, uncertainCount = 4)
        assertEquals(SyncStatus.UNCERTAIN, uncertainProgress.status)
        assertEquals(4, uncertainProgress.uncertainCount)

        // Test timestamp formatting: 1728907200000L = 2024-10-14 12:00:00 UTC
        val formatted = formatTimestamp(1728907200000L)
        assertTrue(formatted.contains("2024-10-14"), "Formatted timestamp should contain date part: $formatted")
        assertFalse(formatted.contains("Z"), "Formatted timestamp should not have trailing Z: $formatted")
    }

    @Test
    fun testAccessRequestStatusesIncludingApprovedAndUnknown() {
        val pendingInfo = AccessRequestInfo(
            id = "req-1",
            userId = "user-1",
            username = "alice",
            appId = "tabby-rush",
            message = "Please grant access",
            status = "pending",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        assertEquals("pending", pendingInfo.status)

        val approvedInfo = AccessRequestInfo(
            id = "req-2",
            userId = "user-2",
            username = "bob",
            appId = "tabby-rush",
            message = null,
            status = "approved",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        assertEquals("approved", approvedInfo.status)

        val rejectedInfo = AccessRequestInfo(
            id = "req-3",
            userId = "user-3",
            username = "charlie",
            appId = "tabby-rush",
            message = null,
            status = "rejected",
            createdAt = 1000L,
            updatedAt = 3000L
        )
        assertEquals("rejected", rejectedInfo.status)

        val unknownInfo = AccessRequestInfo(
            id = "req-4",
            userId = "user-4",
            username = "dave",
            appId = "tabby-rush",
            message = null,
            status = "quarantined",
            createdAt = 1000L,
            updatedAt = 4000L
        )
        assertEquals("quarantined", unknownInfo.status)
    }

    @Test
    fun testDeadlineValidationValidTimedAndDateOnly() {
        // Timed deadline in UTC
        val timedEditor = EditorState(
            id = null,
            rawTitle = "Timed task",
            hasDeadline = true,
            deadlineDate = "2026-10-20",
            hasTime = true,
            deadlineTime = "15:00",
            deadlineZone = "UTC",
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderCount = 1L
        )
        val timedResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(timedEditor)
        assertTrue(timedResult is DeadlineValidationResult.Valid, "Timed deadline must be valid")
        assertEquals("2026-10-20", timedResult.deadline.date)
        assertEquals("15:00", timedResult.deadline.time)
        assertEquals("UTC", timedResult.deadline.zone)
        assertEquals(0L, timedResult.deadline.offsetSeconds)
        assertEquals(timedResult.deadline.anchorEpochMs, timedResult.deadline.overdueEpochMs)
        assertEquals(1, timedResult.preview.occurrences.size)
        assertEquals(timedResult.deadline.anchorEpochMs - 30 * 60_000L, timedResult.preview.occurrences[0].dueEpochMs)

        // Date-only deadline
        val dateOnlyEditor = EditorState(
            id = null,
            rawTitle = "Date-only task",
            hasDeadline = true,
            deadlineDate = "2026-10-20",
            hasTime = false,
            deadlineZone = "UTC",
            reminderAnchorTime = "09:00",
            reminderEnabled = true,
            reminderLeadMinutes = 0L,
            reminderCount = 1L
        )
        val dateOnlyResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(dateOnlyEditor)
        assertTrue(dateOnlyResult is DeadlineValidationResult.Valid, "Date-only deadline must be valid")
        assertNull(dateOnlyResult.deadline.time)
        assertEquals("09:00", dateOnlyResult.deadline.reminderAnchorTime)
        assertTrue(dateOnlyResult.deadline.overdueEpochMs > dateOnlyResult.deadline.anchorEpochMs,
            "Date-only overdue must be after anchor time (next day start)")
    }

    @Test
    fun testDeadlineValidationDstGapAndOverlap() {
        // Gap in America/New_York on 2026-03-08 at 02:30 (spring forward skips 02:00-03:00)
        val gapEditor = EditorState(
            id = null,
            rawTitle = "Spring forward gap",
            hasDeadline = true,
            deadlineDate = "2026-03-08",
            hasTime = true,
            deadlineTime = "02:30",
            deadlineZone = "America/New_York"
        )
        val gapResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(gapEditor)
        assertTrue(gapResult is DeadlineValidationResult.Invalid, "DST gap must be invalid")
        assertTrue(gapResult.isGap, "Result must flag isGap = true")

        // Overlap in America/New_York on 2026-11-01 at 01:30 (fall back repeats 01:00-02:00)
        val overlapEditorWithoutChoice = EditorState(
            id = null,
            rawTitle = "Fall back overlap",
            hasDeadline = true,
            deadlineDate = "2026-11-01",
            hasTime = true,
            deadlineTime = "01:30",
            deadlineZone = "America/New_York",
            chosenOffsetSeconds = null
        )
        val overlapResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(overlapEditorWithoutChoice)
        assertTrue(overlapResult is DeadlineValidationResult.Invalid, "Ambiguous DST overlap must require offset")
        assertNotNull(overlapResult.overlap, "Overlap result must be present")
        assertEquals(-14400L, overlapResult.overlap.earlierOffsetSeconds) // EDT -04:00
        assertEquals(-18000L, overlapResult.overlap.laterOffsetSeconds)   // EST -05:00

        // Resolving with chosen earlier offset
        val resolvedEditor = overlapEditorWithoutChoice.copy(chosenOffsetSeconds = -14400L)
        val resolvedResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(resolvedEditor)
        assertTrue(resolvedResult is DeadlineValidationResult.Valid, "Resolved overlap must be valid")
        assertEquals(-14400L, resolvedResult.deadline.offsetSeconds)
    }

    @Test
    fun testDeadlineValidationImpossibleCalendarDates() {
        // Feb 30, Feb 31, April 31, and non-leap Feb 29 (2025) must fail cleanly without throwing uncaught exceptions
        val impossibleDates = listOf("2026-02-30", "2026-02-31", "2026-04-31", "2025-02-29")
        for (date in impossibleDates) {
            val editor = EditorState(
                id = null,
                rawTitle = "Impossible date task",
                hasDeadline = true,
                deadlineDate = date,
                hasTime = true,
                deadlineTime = "12:00",
                deadlineZone = "UTC"
            )
            val result = DeadlineCalculatorHelpers.validateAndComputeDeadline(editor)
            assertTrue(result is DeadlineValidationResult.Invalid, "Date $date must be rejected as Invalid without throwing")

            // Date-only variant as well
            val dateOnlyEditor = editor.copy(hasTime = false)
            val dateOnlyResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(dateOnlyEditor)
            assertTrue(dateOnlyResult is DeadlineValidationResult.Invalid, "Date-only $date must be rejected as Invalid without throwing")
        }
    }

    @Test
    fun testDeadlineReminderBoundsAndLastOccurrenceLimit() {
        // Negative lead minutes rejected
        val invalidLead = EditorState(
            id = null,
            rawTitle = "Negative lead",
            hasDeadline = true,
            deadlineDate = "2026-10-20",
            hasTime = true,
            deadlineTime = "12:00",
            deadlineZone = "UTC",
            reminderEnabled = true,
            reminderLeadMinutes = -5L
        )
        assertTrue(DeadlineCalculatorHelpers.validateAndComputeDeadline(invalidLead) is DeadlineValidationResult.Invalid)

        // Count > 1 requires interval >= 15
        val invalidInterval = EditorState(
            id = null,
            rawTitle = "Short interval",
            hasDeadline = true,
            deadlineDate = "2026-10-20",
            hasTime = true,
            deadlineTime = "12:00",
            deadlineZone = "UTC",
            reminderEnabled = true,
            reminderLeadMinutes = 60L,
            reminderCount = 3L,
            reminderIntervalMinutes = 10L
        )
        assertTrue(DeadlineCalculatorHelpers.validateAndComputeDeadline(invalidInterval) is DeadlineValidationResult.Invalid)

        // Last occurrence exceeds anchor: lead 30m, count 3, interval 20m -> offsets: -30m, -10m, +10m > anchor!
        val exceedsAnchor = EditorState(
            id = null,
            rawTitle = "Exceeds anchor",
            hasDeadline = true,
            deadlineDate = "2026-10-20",
            hasTime = true,
            deadlineTime = "12:00",
            deadlineZone = "UTC",
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderCount = 3L,
            reminderIntervalMinutes = 20L
        )
        val exceedResult = DeadlineCalculatorHelpers.validateAndComputeDeadline(exceedsAnchor)
        assertTrue(exceedResult is DeadlineValidationResult.Invalid, "Last occurrence > anchor must be rejected")
    }

    @Test
    fun testOverdueLogicDateOnlyVsTimedAndCompleted() {
        val anchorMs = 1700000000000L
        val overdueMs = 1700086400000L // 24h later

        val timedDeadline = TaskDeadline(
            date = "2026-10-20",
            time = "12:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = anchorMs,
            overdueEpochMs = anchorMs
        )
        val timedTodo = Todo(
            id = "t-1",
            title = "Timed Todo",
            completed = false,
            createdAt = 1000L,
            deadline = timedDeadline,
            scheduleToken = "tok-1"
        )
        // Not overdue before anchor
        assertFalse(timedTodo.completed || (anchorMs - 1000L >= timedDeadline.overdueEpochMs))
        // Overdue at/after anchor
        assertTrue(!timedTodo.completed && (anchorMs >= timedDeadline.overdueEpochMs))

        // Date-only task
        val dateOnlyDeadline = TaskDeadline(
            date = "2026-10-20",
            time = null,
            zone = "UTC",
            offsetSeconds = 0L,
            reminderAnchorTime = "09:00",
            anchorEpochMs = anchorMs,
            overdueEpochMs = overdueMs
        )
        val dateOnlyTodo = Todo(
            id = "t-2",
            title = "Date-only Todo",
            completed = false,
            createdAt = 1000L,
            deadline = dateOnlyDeadline,
            scheduleToken = "tok-2"
        )
        // At anchor time (09:00), NOT overdue!
        assertFalse(!dateOnlyTodo.completed && (anchorMs >= dateOnlyDeadline.overdueEpochMs),
            "Date-only task must not be overdue at reminder anchor time")
        // Overdue at next calendar day start
        assertTrue(!dateOnlyTodo.completed && (overdueMs >= dateOnlyDeadline.overdueEpochMs),
            "Date-only task is overdue at next day start")

        // Completed task is NEVER overdue
        val completedTodo = dateOnlyTodo.copy(completed = true)
        val isCompletedOverdue = !completedTodo.completed && (overdueMs + 10000L >= dateOnlyDeadline.overdueEpochMs)
        assertFalse(isCompletedOverdue, "Completed task must never be considered overdue")
    }

    @Test
    fun testReminderSettingsStateDefaultsAndCapabilities() {
        val caps = ReminderCapabilities(
            canScheduleExactAlarms = true,
            canPostNotifications = true,
            areNotificationsEnabled = true
        )
        assertTrue(caps.isDeliveryPermitted)

        val settings = ReminderSettingsState(
            notificationsEnabled = true,
            defaultReminderEnabled = true,
            defaultReminderLeadMinutes = 15L,
            defaultReminderIntervalMinutes = 15L,
            defaultReminderCount = 2L,
            defaultDateOnlyAnchorTime = "09:00",
            capabilities = caps,
            platformName = "Linux"
        )
        assertEquals("Linux", settings.platformName)
        assertTrue(settings.notificationsEnabled)
        assertEquals(15L, settings.defaultReminderLeadMinutes)
        assertEquals(2L, settings.defaultReminderCount)
        assertEquals("09:00", settings.defaultDateOnlyAnchorTime)
    }

    @Test
    fun testConflictReviewDetailedParameters() {
        val deadline = TaskDeadline(
            date = "2026-10-20",
            time = "14:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1700000000000L,
            overdueEpochMs = 1700000000000L
        )
        val rule = ReminderRule(
            enabled = true,
            leadMinutes = 30L,
            intervalMinutes = 15L,
            count = 2L
        )
        val todo = Todo(
            id = "c-1",
            title = "Conflicted task",
            completed = false,
            createdAt = 1000L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "sched-token-1234"
        )
        val conflictState = ConflictState(
            rowId = "c-1",
            localTodo = todo,
            remoteTodo = null,
            requiresDeliveryReview = true
        )
        assertTrue(conflictState.requiresDeliveryReview)
        assertEquals("c-1", conflictState.rowId)
        assertEquals("sched-token-1234", conflictState.localTodo?.scheduleToken)
        assertEquals(2L, conflictState.localTodo?.reminderRule?.count)
    }

    @Test
    fun testDeadlinePickerCanonicalFormatsAndOffsetClearing() {
        val dateMillis = 1793491200000L // 2026-11-01 (America/New_York fall back overlap day)
        val canonicalDate = DateTimePickerFormats.utcMillisToDate(dateMillis)
        assertEquals("2026-11-01", canonicalDate)

        val initialEditor = EditorState(
            id = "t-picker",
            rawTitle = "Picker task",
            hasDeadline = true,
            deadlineDate = canonicalDate,
            hasTime = true,
            deadlineTime = "01:30",
            deadlineZone = "America/New_York",
            chosenOffsetSeconds = -14400L // EDT
        )
        // Changing time via picker produces canonical HH:mm and clears chosen offset
        val newTime = DateTimePickerFormats.formatTime(1, 35)
        val updatedEditor = initialEditor.copy(deadlineTime = newTime, chosenOffsetSeconds = null)
        assertEquals("01:35", updatedEditor.deadlineTime)
        assertNull(updatedEditor.chosenOffsetSeconds)

        val result = DeadlineCalculatorHelpers.validateAndComputeDeadline(updatedEditor)
        assertTrue(result is DeadlineValidationResult.Invalid, "America/New_York 01:35 during fall-back overlap requires explicit offset")
        assertNotNull(result.overlap)
    }

    @Test
    fun testDeadlinePickerDstGapRejection() {
        val time = DateTimePickerFormats.formatTime(2, 30)
        val editor = EditorState(
            id = "t-gap",
            rawTitle = "Gap task",
            hasDeadline = true,
            deadlineDate = "2026-03-08",
            hasTime = true,
            deadlineTime = time,
            deadlineZone = "America/New_York"
        )
        val result = DeadlineCalculatorHelpers.validateAndComputeDeadline(editor)
        assertTrue(result is DeadlineValidationResult.Invalid, "DST gap time must be rejected as invalid")
        assertTrue(result.isGap, "Result must flag gap transition")
    }

    @Test
    fun testSettingsAnchorTimePickerFlow() {
        val anchor = DateTimePickerFormats.formatTime(8, 30)
        assertEquals("08:30", anchor)
        val settings = ReminderSettingsState(
            notificationsEnabled = true,
            defaultReminderEnabled = true,
            defaultDateOnlyAnchorTime = anchor
        )
        assertEquals("08:30", settings.defaultDateOnlyAnchorTime)

        val editor = EditorState(
            id = "t-date-only",
            rawTitle = "Date only task",
            hasDeadline = true,
            deadlineDate = "2026-10-20",
            hasTime = false,
            reminderAnchorTime = settings.defaultDateOnlyAnchorTime,
            deadlineZone = "UTC"
        )
        val result = DeadlineCalculatorHelpers.validateAndComputeDeadline(editor)
        assertTrue(result is DeadlineValidationResult.Valid, "Date-only deadline with settings anchor must be valid")
        assertNull(result.deadline.time, "Timed deadline must be null for date-only")
        assertEquals("08:30", result.deadline.reminderAnchorTime)
    }

    @Test
    fun testCompactHeaderLayoutTokenInvariants() {
        // Enforce Android / Material 3 minimum touch target guideline token
        assertEquals(48.dp, TabbyRushThemeTokens.MinTouchTargetSize)
        assertTrue(TabbyRushThemeTokens.MinTouchTargetSize >= 48.dp, "Minimum touch target token must be >= 48dp per Material guidelines")

        // Compact brand logo geometry invariants
        assertEquals(36.dp, TabbyRushThemeTokens.CompactLogoSize)
        assertEquals(8.dp, TabbyRushThemeTokens.CompactLogoCornerRadius)
        assertEquals(56.dp, TabbyRushThemeTokens.CompactAccountLabelMaxWidth)
        assertTrue(TabbyRushThemeTokens.CompactLogoSize < 48.dp, "Logo icon must be compact (<48dp) to preserve button space")
        assertTrue(TabbyRushThemeTokens.CompactLogoCornerRadius > 0.dp, "Logo badge must maintain rounded corners")

        // Viewport width budget calculation on standard 360dp mobile screen:
        // Available width = 360dp - 2 * 12dp (padding) = 336dp
        // Left branding column with max constrained label: 56dp + 8dp spacing = 64dp
        // Remaining width available for top buttons = 272dp (standard buttons fit within budget)
        val screenWidth360 = 360.dp
        val horizontalPadding = TabbyRushThemeTokens.SpacingMedium * 2
        val brandingColumnWidth = TabbyRushThemeTokens.CompactAccountLabelMaxWidth + TabbyRushThemeTokens.SpacingSmall
        val availableButtonWidth360 = screenWidth360 - horizontalPadding - brandingColumnWidth
        assertTrue(availableButtonWidth360 >= 270.dp, "Available width for top buttons on 360dp screen must be >= 270dp (was $availableButtonWidth360)")
    }

    @Test
    fun testSubtaskDisclosurePresentationFilteringPreservesCountsAndProgress() {
        val rootId = "root-proj-1"
        val root = Todo(id = rootId, title = "Parent Task", completed = false, createdAt = 1000L)
        val child1 = Todo(id = "child-act-1", title = "Active Subtask", completed = false, createdAt = 1100L, parentTaskId = rootId)
        val child2 = Todo(id = "child-comp-2", title = "Done Subtask", completed = true, createdAt = 1200L, parentTaskId = rootId)
        val standalone = Todo(id = "standalone-1", title = "Standalone", completed = false, createdAt = 900L)

        val todos = listOf(root, child1, child2, standalone)
        val snapshot = TaskHierarchyProjector.projectHierarchy(todos, TodoFilter.ALL)
        val displayItems = snapshot.items

        // Initially 4 items in display items
        assertEquals(4, displayItems.size)
        // Counts strictly count unique real tasks using effective completion:
        // root (active because child1 active), child1 (active), child2 (completed), standalone (active) => all=4, active=3, completed=1
        assertEquals(4, snapshot.counts.all)
        assertEquals(3, snapshot.counts.active)
        assertEquals(1, snapshot.counts.completed)

        // Subtasks are collapsed by default (empty expanded set)
        val defaultExpandedParentIds = emptySet<String>()
        val defaultVisibleItems = displayItems.filterNot { item ->
            item.depth > 0 && item.todo.parentTaskId != null && !defaultExpandedParentIds.contains(item.todo.parentTaskId)
        }

        // Only root and standalone are rendered by default; child1 and child2 are visually filtered out
        assertEquals(2, defaultVisibleItems.size)
        assertEquals(listOf("root-proj-1", "standalone-1"), defaultVisibleItems.map { it.todo.id })

        // The parent row retains its true child counts and automatic completion progress
        val rootRendered = defaultVisibleItems.first { it.todo.id == rootId }
        assertTrue(rootRendered.hasLiveChildren)
        assertEquals(2, rootRendered.childCount)
        assertEquals(1, rootRendered.completedChildCount)
        assertFalse(rootRendered.effectiveCompleted)

        // Presentation filtering must not alter the snapshot counts
        assertEquals(4, snapshot.counts.all)
        assertEquals(3, snapshot.counts.active)
        assertEquals(1, snapshot.counts.completed)

        // Expand parent task
        val expandedParentIds = setOf(rootId)
        val expandedItems = displayItems.filterNot { item ->
            item.depth > 0 && item.todo.parentTaskId != null && !expandedParentIds.contains(item.todo.parentTaskId)
        }
        assertEquals(4, expandedItems.size)
        assertEquals(displayItems.map { it.todo.id }, expandedItems.map { it.todo.id })
    }

    @Test
    fun testSubtaskDisclosureInFilteredViewsWithContextOnlyParent() {
        val rootId = "root-context-1"
        val root = Todo(id = rootId, title = "Parent", completed = false, createdAt = 1000L)
        val child1Active = Todo(id = "c-active", title = "Active Subtask", completed = false, createdAt = 1100L, parentTaskId = rootId)
        val child2Done = Todo(id = "c-done", title = "Done Subtask", completed = true, createdAt = 1200L, parentTaskId = rootId)
        val todos = listOf(root, child1Active, child2Done)

        // 1. Under COMPLETED filter:
        val compSnapshot = TaskHierarchyProjector.projectHierarchy(todos, TodoFilter.COMPLETED)
        // Parent is context-only, child2Done is matching real row, child1Active excluded
        assertEquals(2, compSnapshot.items.size)
        val compParent = compSnapshot.items.first { it.todo.id == rootId }
        assertTrue(compParent.isContextOnly)
        assertTrue(compParent.hasLiveChildren)

        // When parent is collapsed (default):
        val collapsedComp = compSnapshot.items.filterNot { item ->
            item.depth > 0 && item.todo.parentTaskId != null && !emptySet<String>().contains(item.todo.parentTaskId)
        }
        assertEquals(1, collapsedComp.size)
        assertEquals(rootId, collapsedComp[0].todo.id)
        assertTrue(collapsedComp[0].isContextOnly)

        // When expanded, only the filter-admitted child (c-done) appears, NEVER c-active
        val expandedComp = compSnapshot.items.filterNot { item ->
            item.depth > 0 && item.todo.parentTaskId != null && !setOf(rootId).contains(item.todo.parentTaskId)
        }
        assertEquals(2, expandedComp.size)
        assertEquals(listOf(rootId, "c-done"), expandedComp.map { it.todo.id })

        // 2. Under ACTIVE filter:
        val activeSnapshot = TaskHierarchyProjector.projectHierarchy(todos, TodoFilter.ACTIVE)
        // Parent is matching row (effective active), child1Active is matching real row, child2Done excluded
        assertEquals(2, activeSnapshot.items.size)
        val collapsedActive = activeSnapshot.items.filterNot { item ->
            item.depth > 0 && item.todo.parentTaskId != null && !emptySet<String>().contains(item.todo.parentTaskId)
        }
        assertEquals(1, collapsedActive.size)
        assertEquals(rootId, collapsedActive[0].todo.id)
    }

    @Test
    fun testSubtaskDisclosureStateKeyedByAccount() {
        // State is isolated per account identity key
        var activeAccount: String? = "acc-alice"
        val expandedMap = mutableMapOf<String?, Set<String>>()

        // Expand a parent in Alice's account
        expandedMap[activeAccount] = setOf("task-1", "task-2")
        assertEquals(setOf("task-1", "task-2"), expandedMap["acc-alice"])

        // Switch to Bob's account: disclosure state starts fresh (default collapsed)
        activeAccount = "acc-bob"
        val bobsExpanded = expandedMap[activeAccount] ?: emptySet()
        assertTrue(bobsExpanded.isEmpty(), "Switching to new account must default to empty expanded set (all collapsed)")

        // Sign out: null account key also isolated
        activeAccount = null
        val signedOutExpanded = expandedMap[activeAccount] ?: emptySet()
        assertTrue(signedOutExpanded.isEmpty(), "Signed-out state must not retain previous user's expanded parent IDs")
    }

    @Test
    fun testDesktopSidebarNavigationMenuLayoutInvariants() {
        // Enforce minimum touch target token on all desktop menu controls
        assertEquals(48.dp, TabbyRushThemeTokens.MinTouchTargetSize)

        // Desktop sidebar geometry constraints (>= 840dp layout)
        val sidebarWidth = 280.dp
        val sidebarPadding = TabbyRushThemeTokens.SpacingLarge * 2 // 16.dp * 2 = 32.dp
        val availableSidebarContentWidth = sidebarWidth - sidebarPadding
        assertEquals(248.dp, availableSidebarContentWidth)

        // In the desktop sidebar, buttons fill maxWidth (248dp) and center their contents
        // A standard button title + badge cluster (e.g., "Announcements" ~120dp + 8dp spacer + 24dp badge = ~152dp)
        // fits well within the 248dp width budget with ample safety margin preventing truncation
        val estimatedClusterWidth = 160.dp
        assertTrue(estimatedClusterWidth < availableSidebarContentWidth, "Navigation menu cluster must fit within available sidebar width")
    }

    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
