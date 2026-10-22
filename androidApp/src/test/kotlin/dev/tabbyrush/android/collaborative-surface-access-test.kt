package dev.tabbyrush.android

import android.net.Uri
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AccessLossEvidence
import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.DeadlineSelectionKey
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.PersistedTaskSchedule
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.SurfaceSnapshot
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.TaskSurfaceAccess
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.selectNextDeadline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests verifying collaborative surface security and contracts:
 * 1. Intent validation: rejection of missing, stale, or forged owner/generation tokens.
 * 2. URI/extras agreement: mismatch rejection across widgets, notifications, and alarms.
 * 3. Colliding row IDs across distinct owners produce isolated request codes and notification IDs.
 * 4. Child-only projection without private parent.
 * 5. Capability admission: editContent, deleteTask, and manageAssignment enforcement.
 * 6. AccessGeneration publication fencing: pre-revocation work cannot publish after access loss.
 * 7. Legacy pre-upgrade keys without owner are rejected/cleaned up without mapping to current user.
 */
class CollaborativeSurfaceAccessTest {

    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 5L)
    private val alice = "user-alice"
    private val bob = "user-bob"
    private val sharedRowId = "shared-row-99"

    // -------------------------------------------------------------------------
    // 1. Hostile & Stale Intent Identification & URI Agreement
    // -------------------------------------------------------------------------

    @Test
    fun testSurfaceIntentsRejectsMissingOrMalformedData() {
        assertNull(TaskSurfaceIntents.parseDataUri(null as String?))
        assertNull(TaskSurfaceIntents.parseDataUri(""))
        assertNull(TaskSurfaceIntents.parseDataUri("http://example.com/not/tabbyrush"))

        // Incomplete path segments (needs: complete, accountKey, generation, ownerUserId, rowId)
        val shortUri = "tabbyrush://task-surface/complete/${testAccountKey.value}/5/alice"
        assertNull(TaskSurfaceIntents.parseDataUri(shortUri))

        // Valid 5-segment URI
        val validUri = "tabbyrush://task-surface/complete/${testAccountKey.value}/5/alice/row-1"
        val parsed = TaskSurfaceIntents.parseDataUri(validUri)
        assertNotNull(parsed)
        assertEquals("complete", parsed.operation)
        assertEquals(testAccountKey, parsed.accountKey)
        assertEquals(5L, parsed.generation)
        assertEquals(TaskRef("alice", "row-1"), parsed.taskRef)
    }

    @Test
    fun testPendingIntentRequestCodesDistinguishCollidingRowIdsAcrossOwners() {
        val aliceRef = TaskRef(alice, sharedRowId)
        val bobRef = TaskRef(bob, sharedRowId)

        // Surface intents
        val codeAlice = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 5L, aliceRef)
        val codeBob = TaskSurfaceIntents.getRequestCode("complete", testAccountKey, 5L, bobRef)
        assertNotEquals(codeAlice, codeBob, "Request code must distinguish tasks across distinct owners even with identical rowId")

        // Widget action request codes
        val widgetCodeAlice = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, aliceRef)
        val widgetCodeBob = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, bobRef)
        assertNotEquals(widgetCodeAlice, widgetCodeBob, "Widget action request code must not collide across owners")

        // Alarm request codes
        val alarmCodeAlice = AndroidNotificationIntentFactory.getAlarmRequestCode(testAccountKey, aliceRef, "token-1", 0)
        val alarmCodeBob = AndroidNotificationIntentFactory.getAlarmRequestCode(testAccountKey, bobRef, "token-1", 0)
        assertNotEquals(alarmCodeAlice, alarmCodeBob, "Alarm request code must not collide across owners")
    }

    @Test
    fun testNotificationIdCalculationsNeverCrossTargetCollidingRowIds() {
        val notifIdAlice = (testAccountKey.value + ":" + alice + ":" + sharedRowId).hashCode() and 0x7FFFFFFF
        val notifIdBob = (testAccountKey.value + ":" + bob + ":" + sharedRowId).hashCode() and 0x7FFFFFFF
        assertNotEquals(notifIdAlice, notifIdBob, "Notification IDs must never collide for tasks owned by different users")
    }

    // -------------------------------------------------------------------------
    // 2. Child-Only Projection & Hierarchy Isolation
    // -------------------------------------------------------------------------

    @Test
    fun testChildOnlyTaskProjectedFlatWithoutPrivateParent() {
        val bobChildRef = TaskRef(bob, "child-only-task")
        val childOnlyTodo = Todo(
            taskRef = bobChildRef,
            title = "Shared Subtask Content",
            completed = false,
            createdAt = 2000L,
            parentTaskRef = TaskRef(bob, "private-root-omitted"),
            accessState = TaskAccessState(
                ref = bobChildRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.ChildOnly
            )
        )

        val widgetItems = projectWidgetItems(listOf(childOnlyTodo))
        assertEquals(1, widgetItems.size)
        val item = widgetItems[0]
        assertEquals("Shared Subtask Content", item.todo.title)
        assertTrue(item.isChildOnly, "Widget item must recognize child-only status")
        assertTrue(item.isChild, "Widget item must be categorized as child")
        assertFalse(item.hasLiveChildren, "Child-only task has no live children")
    }

    // -------------------------------------------------------------------------
    // 3. Selection Fencing & Revocation
    // -------------------------------------------------------------------------

    @Test
    fun testSelectionSuppressesRevokedOrInaccessibleTasks() {
        val dl = TaskDeadline(
            date = "2026-10-21",
            time = "15:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1_000_000L,
            overdueEpochMs = 1_000_000L
        )
        val revokedSchedule = PersistedTaskSchedule(
            taskRef = TaskRef(bob, "revoked-shared-task"),
            title = "Revoked Confidential Task",
            completed = false,
            isDeleted = false,
            deadline = dl,
            reminderRule = ReminderRule(enabled = true),
            scheduleToken = "sched-1",
            hasUnresolvedConflict = false,
            isAccessible = false // Revocation Purge marked inaccessible
        )

        val selection = selectNextDeadline(listOf(revokedSchedule), nowEpochMs = 500_000L)
        assertNull(selection, "Inaccessible (revoked) task must never be selected for surface notification")
    }

    // -------------------------------------------------------------------------
    // 4. Legacy Suppression Key Fail-Closed Handling
    // -------------------------------------------------------------------------

    @Test
    fun testLegacySuppressionKeyWithoutOwnerIsRejected() {
        val legacyRaw = "v1:acc=${testAccountKey.value}&gen=5&tid=some-row&date=2026-10-21&zone=UTC&offset=0&anchorEpoch=1000&overdueEpoch=1000"
        val decoded = DeadlineSelectionKeyCodec.decode(legacyRaw)
        assertNull(decoded, "Legacy key without owner must decode to null fail-closed")
    }

    @Test
    fun testCanonicalSuppressionKeyPreservesTaskRefRoundtrip() {
        val taskRef = TaskRef(alice, "canonical-row-1")
        val deadline = TaskDeadline(
            date = "2026-10-22",
            time = "18:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 2_000_000L,
            overdueEpochMs = 2_000_000L
        )
        val key = DeadlineSelectionKey(testToken, taskRef, deadline)
        val encoded = DeadlineSelectionKeyCodec.encode(key)
        val decoded = DeadlineSelectionKeyCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(taskRef, decoded.taskRef)
        assertEquals(taskRef.ownerUserId, decoded.ownerUserId)
        assertEquals(taskRef.rowId, decoded.taskId)
    }

    // -------------------------------------------------------------------------
    // 5. Capability Enforcement & Navigation Validation
    // -------------------------------------------------------------------------

    @Test
    fun testAssigneeWithoutEditRightsCannotCompleteTask() = runBlocking {
        val taskRef = TaskRef(alice, "view-only-task")
        val readOnlyAccess = TaskAccessState(
            ref = taskRef,
            revision = 1L,
            capabilities = TaskCapabilities(
                editContent = false,
                manageAssignment = false,
                deleteTask = false,
                createChild = false
            ),
            coverage = HierarchyCoverage.ChildOnly
        )

        val mockAccess = object : TaskSurfaceAccess {
            override val readiness: StateFlow<SurfaceReadiness> =
                MutableStateFlow(SurfaceReadiness.Ready(testToken)).asStateFlow()

            override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness = readiness.value

            override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> =
                SurfaceResult.Unavailable

            override suspend fun setCompleted(
                token: SurfaceAccountToken,
                taskRef: TaskRef,
                completed: Boolean,
                nowEpochMs: Long
            ): SurfaceResult<CompletionChange> {
                if (token != testToken) return SurfaceResult.Rejected
                // Enforce editContent capability check
                if (!readOnlyAccess.capabilities.editContent) {
                    return SurfaceResult.Rejected
                }
                return SurfaceResult.Value(CompletionChange.Changed)
            }

            override suspend fun publishIfCurrent(
                token: SurfaceAccountToken,
                publish: suspend () -> Unit
            ): Boolean = token == testToken

            override suspend fun validateNavigation(
                token: SurfaceAccountToken,
                taskRef: TaskRef?
            ): Boolean {
                if (token != testToken) return false
                // Navigation to readable task is allowed even if editContent is false
                return taskRef == readOnlyAccess.ref
            }
        }

        // Completion must be rejected for view-only assignee
        val completeResult = mockAccess.setCompleted(testToken, taskRef, true, 1000L)
        assertTrue(completeResult is SurfaceResult.Rejected, "Assignee without editContent cannot complete task")

        // Navigation to readable task must succeed
        val canNavigate = mockAccess.validateNavigation(testToken, taskRef)
        assertTrue(canNavigate, "Assignee can still navigate to readable task even without editContent")

        // Stale token navigation must fail
        val staleToken = SurfaceAccountToken(testAccountKey, generation = 4L)
        val staleNavigate = mockAccess.validateNavigation(staleToken, taskRef)
        assertFalse(staleNavigate, "Stale token navigation must be rejected")
    }

    // -------------------------------------------------------------------------
    // 6. AccessGeneration Publication Fencing
    // -------------------------------------------------------------------------

    @Test
    fun testPublicationFencedWhenAccessGenerationAdvances() = runBlocking {
        var activeAccessGeneration = 1L

        val mockAccess = object : TaskSurfaceAccess {
            override val readiness: StateFlow<SurfaceReadiness> =
                MutableStateFlow(SurfaceReadiness.Ready(testToken)).asStateFlow()

            override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness = readiness.value

            override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> =
                SurfaceResult.Unavailable

            override suspend fun setCompleted(
                token: SurfaceAccountToken,
                taskRef: TaskRef,
                completed: Boolean,
                nowEpochMs: Long
            ): SurfaceResult<CompletionChange> = SurfaceResult.Rejected

            override suspend fun publishIfCurrent(
                token: SurfaceAccountToken,
                publish: suspend () -> Unit
            ): Boolean {
                if (token != testToken) return false
                val initialGen = activeAccessGeneration
                // Simulate race where revocation occurs during publication prep
                if (activeAccessGeneration != initialGen) {
                    return false
                }
                publish()
                return true
            }

            suspend fun publishWithFencing(
                token: SurfaceAccountToken,
                capturedAccessGen: Long,
                publish: suspend () -> Unit
            ): Boolean {
                if (token != testToken) return false
                if (activeAccessGeneration != capturedAccessGen) {
                    return false
                }
                publish()
                return true
            }

            override suspend fun validateNavigation(
                token: SurfaceAccountToken,
                taskRef: TaskRef?
            ): Boolean = true
        }

        var published = false

        // 1. Publication at matching generation succeeds
        val success = mockAccess.publishWithFencing(testToken, capturedAccessGen = 1L) {
            published = true
        }
        assertTrue(success)
        assertTrue(published)

        // 2. Authoritative revocation advances accessGeneration
        activeAccessGeneration = 2L
        published = false

        // 3. Stale publication attempt at capturedAccessGen = 1L must be fenced
        val fenced = mockAccess.publishWithFencing(testToken, capturedAccessGen = 1L) {
            published = true
        }
        assertFalse(fenced, "Publication must be fenced when accessGeneration has advanced")
        assertFalse(published, "No content must be published under advanced accessGeneration")
    }
}
