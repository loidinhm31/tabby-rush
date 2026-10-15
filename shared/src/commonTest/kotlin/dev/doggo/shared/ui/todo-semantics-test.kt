package dev.doggo.shared.ui

import androidx.compose.ui.graphics.Color
import dev.doggo.shared.contract.AccessRequestInfo
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.ConflictState
import dev.doggo.shared.contract.EditorState
import dev.doggo.shared.contract.LeaveGuardState
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.SyncProgress
import dev.doggo.shared.contract.SyncStatus
import dev.doggo.shared.contract.TitlePolicy
import dev.doggo.shared.contract.TaskDeadline
import dev.doggo.shared.contract.ReminderRule
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.contract.TodoCounts
import dev.doggo.shared.contract.TodoFilter
import dev.doggo.shared.contract.TodoUiState
import dev.doggo.shared.theme.DoggoCoral
import dev.doggo.shared.theme.DoggoCoralSurface
import dev.doggo.shared.theme.DoggoCream
import dev.doggo.shared.theme.DoggoInk
import dev.doggo.shared.theme.DoggoMintSurface
import dev.doggo.shared.theme.DoggoMuted
import dev.doggo.shared.theme.DoggoOutline
import dev.doggo.shared.theme.DoggoPage
import dev.doggo.shared.theme.DoggoPrimary
import dev.doggo.shared.theme.DoggoPrimaryDeep
import dev.doggo.shared.theme.DoggoWhite
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

class TodoSemanticsTest {

    @Test
    fun testWcagContrastRatiosMeetAccessibilityStandards() {
        // Assert WCAG 2.1 AA requirements (>= 4.5:1 for normal text, >= 3.0:1 for non-text controls)
        val inkOnPage = calculateContrastRatio(DoggoInk, DoggoPage)
        assertTrue(inkOnPage >= 4.5, "Ink on Page must be >= 4.5:1 (was $inkOnPage)")

        val inkOnWhite = calculateContrastRatio(DoggoInk, DoggoWhite)
        assertTrue(inkOnWhite >= 4.5, "Ink on White must be >= 4.5:1 (was $inkOnWhite)")

        val inkOnCream = calculateContrastRatio(DoggoInk, DoggoCream)
        assertTrue(inkOnCream >= 4.5, "Ink on Cream must be >= 4.5:1 (was $inkOnCream)")

        val inkOnMintSurface = calculateContrastRatio(DoggoInk, DoggoMintSurface)
        assertTrue(inkOnMintSurface >= 4.5, "Ink on MintSurface must be >= 4.5:1 (was $inkOnMintSurface)")

        val inkOnCoralSurface = calculateContrastRatio(DoggoInk, DoggoCoralSurface)
        assertTrue(inkOnCoralSurface >= 4.5, "Ink on CoralSurface must be >= 4.5:1 (was $inkOnCoralSurface)")

        val whiteOnPrimary = calculateContrastRatio(DoggoWhite, DoggoPrimary)
        assertTrue(whiteOnPrimary >= 4.5, "White on Primary teal must be >= 4.5:1 (was $whiteOnPrimary)")

        val whiteOnPrimaryDeep = calculateContrastRatio(DoggoWhite, DoggoPrimaryDeep)
        assertTrue(whiteOnPrimaryDeep >= 4.5, "White on Deep Primary must be >= 4.5:1 (was $whiteOnPrimaryDeep)")

        val whiteOnCoral = calculateContrastRatio(DoggoWhite, DoggoCoral)
        assertTrue(whiteOnCoral >= 4.5, "White on Coral must be >= 4.5:1 (was $whiteOnCoral)")

        val coralOnWhite = calculateContrastRatio(DoggoCoral, DoggoWhite)
        assertTrue(coralOnWhite >= 4.5, "Coral on White must be >= 4.5:1 (was $coralOnWhite)")

        val coralOnCoralSurface = calculateContrastRatio(DoggoCoral, DoggoCoralSurface)
        assertTrue(coralOnCoralSurface >= 4.5, "Coral on CoralSurface must be >= 4.5:1 (was $coralOnCoralSurface)")

        val mutedOnWhite = calculateContrastRatio(DoggoMuted, DoggoWhite)
        assertTrue(mutedOnWhite >= 4.5, "Muted on White must be >= 4.5:1 (was $mutedOnWhite)")

        val mutedOnMintSurface = calculateContrastRatio(DoggoMuted, DoggoMintSurface)
        assertTrue(mutedOnMintSurface >= 4.5, "Muted on MintSurface must be >= 4.5:1 (was $mutedOnMintSurface)")

        val mutedOnCoralSurface = calculateContrastRatio(DoggoMuted, DoggoCoralSurface)
        assertTrue(mutedOnCoralSurface >= 4.5, "Muted on CoralSurface must be >= 4.5:1 (was $mutedOnCoralSurface)")

        val mutedOnCream = calculateContrastRatio(DoggoMuted, DoggoCream)
        assertTrue(mutedOnCream >= 4.5, "Muted on Cream must be >= 4.5:1 (was $mutedOnCream)")

        val primaryDeepOnMintSurface = calculateContrastRatio(DoggoPrimaryDeep, DoggoMintSurface)
        assertTrue(primaryDeepOnMintSurface >= 4.5, "PrimaryDeep on MintSurface must be >= 4.5:1 (was $primaryDeepOnMintSurface)")

        val outlineOnWhite = calculateContrastRatio(DoggoOutline, DoggoWhite)
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
        assertTrue(validResult is dev.doggo.shared.contract.ApiResult.Success)
        assertEquals(validTitle, validResult.value)

        val emptyResult = TitlePolicy.normalizeAndValidate("   ")
        assertTrue(emptyResult is dev.doggo.shared.contract.ApiResult.Failure)
        assertEquals("TITLE_EMPTY", emptyResult.error.code)

        val longTitle = "a".repeat(201)
        val longResult = TitlePolicy.normalizeAndValidate(longTitle)
        assertTrue(longResult is dev.doggo.shared.contract.ApiResult.Failure)
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
            isUnknownSuperseded = true
        )

        assertTrue(conflict.isUnknownSuperseded)
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

        val resetProgress = SyncProgress(status = SyncStatus.RESET_REQUIRED)
        assertEquals(SyncStatus.RESET_REQUIRED, resetProgress.status)

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
            appId = "doggo",
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
            appId = "doggo",
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
            appId = "doggo",
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
            appId = "doggo",
            message = null,
            status = "quarantined",
            createdAt = 1000L,
            updatedAt = 4000L
        )
        assertEquals("quarantined", unknownInfo.status)
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
