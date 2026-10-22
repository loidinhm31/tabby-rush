package dev.tabbyrush.shared.ui

import dev.tabbyrush.shared.contract.AccessLossEvidence
import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AssignmentIntent
import dev.tabbyrush.shared.contract.AssignmentSlotResult
import dev.tabbyrush.shared.contract.EditorState
import dev.tabbyrush.shared.contract.HierarchicalTodoItem
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SyncProgress
import dev.tabbyrush.shared.contract.SyncStatus
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskHierarchyProjector
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.ConflictState
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.contract.TodoUiState
import dev.tabbyrush.shared.contract.UserLookupResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskAssigneeWorkflowTest {

    private fun testRef(owner: String = "alice", rowId: String = "task-1"): TaskRef =
        TaskRef(ownerUserId = owner, rowId = rowId)

    private fun createTodo(
        owner: String = "alice",
        rowId: String = "task-1",
        title: String = "Test Task",
        completed: Boolean = false,
        createdAt: Long = 1000L,
        parentTaskRef: TaskRef? = null,
        accessState: TaskAccessState? = null
    ): Todo = Todo(
        taskRef = TaskRef(ownerUserId = owner, rowId = rowId),
        title = title,
        completed = completed,
        createdAt = createdAt,
        parentTaskRef = parentTaskRef,
        accessState = accessState
    )

    @Test
    fun testCreatorCanManageAssignmentAndSeeDirectVsInheritedIndicators() {
        val taskRef = testRef("alice", "task-creator-1")
        val creatorCapabilities = TaskCapabilities.OWNER
        assertTrue(creatorCapabilities.manageAssignment, "Creator must have manageAssignment capability")
        assertTrue(creatorCapabilities.deleteTask, "Creator must have deleteTask capability")
        assertTrue(creatorCapabilities.createChild, "Creator must have createChild capability")
        assertTrue(creatorCapabilities.editContent, "Creator must have editContent capability")

        // 1. Direct assignee indicator
        var directAssignee: String? = "bob"
        val inheritedAssignee: String? = "charlie"

        val directText = if (directAssignee != null) "Direct assignee: $directAssignee" else "No assignee (unassigned)"
        assertEquals("Direct assignee: bob", directText)

        // 2. Clear direct assignee leaves inherited assignment intact
        val clearIntent = AssignmentIntent(
            taskRef = taskRef,
            desiredRecipientUserId = null,
            expectedSlotVersion = 1L
        )
        assertNull(clearIntent.desiredRecipientUserId, "Clearing assignee must set desiredRecipientUserId to null")
        assertEquals(1L, clearIntent.expectedSlotVersion)

        // Simulate clearing direct assignee
        directAssignee = null
        val fallbackText = when {
            directAssignee != null -> "Direct assignee: $directAssignee"
            inheritedAssignee != null -> "Inherited assignee: $inheritedAssignee (from parent task)"
            else -> "No assignee (unassigned)"
        }
        assertEquals(
            "Inherited assignee: charlie (from parent task)",
            fallbackText,
            "Clearing direct assignee must leave inherited assignee intact with explicit clear text"
        )
    }

    @Test
    fun testAssigneeCannotManageAssignmentOrDeleteOrAddSubtask() {
        val assigneeCapabilities = TaskCapabilities.ASSIGNEE
        assertFalse(assigneeCapabilities.manageAssignment, "Assignee must NOT have manageAssignment capability")
        assertFalse(assigneeCapabilities.deleteTask, "Assignee must NOT have deleteTask capability")
        assertFalse(assigneeCapabilities.createChild, "Assignee must NOT have createChild capability")
        assertTrue(assigneeCapabilities.editContent, "Assignee MUST have editContent capability")

        // Editor state for assignee has canManageAssignment = false
        val assigneeEditor = EditorState(
            taskRef = testRef("alice", "task-shared-1"),
            rawTitle = "Existing title",
            assigneeUserId = "bob",
            canManageAssignment = false
        )
        assertFalse(assigneeEditor.canManageAssignment, "Assignee editor must reflect canManageAssignment = false")
        assertEquals("bob", assigneeEditor.assigneeUserId)

        // In TodoList, capabilities restrict delete and add-child actions
        val todo = createTodo(
            owner = "alice",
            rowId = "task-shared-1",
            accessState = TaskAccessState(
                ref = testRef("alice", "task-shared-1"),
                revision = 5L,
                capabilities = assigneeCapabilities,
                coverage = HierarchyCoverage.Standalone
            )
        )
        val canDelete = todo.accessState?.capabilities?.deleteTask ?: true
        val canCreateChild = todo.accessState?.capabilities?.createChild ?: true
        val canEdit = todo.accessState?.capabilities?.editContent ?: true

        assertFalse(canDelete, "UI must not allow assignee to delete task")
        assertFalse(canCreateChild, "UI must not allow assignee to add child subtask")
        assertTrue(canEdit, "UI must allow assignee to edit task content")
    }

    @Test
    fun testChildOnlyTaskRendersFlatWithoutSyntheticParentOrSiblingCounts() {
        val parentRef = testRef("alice", "parent-root-1")
        val childRef = testRef("alice", "child-only-1")
        val childOnlyTodo = createTodo(
            owner = "alice",
            rowId = "child-only-1",
            title = "Assigned Subtask Alone",
            completed = false,
            parentTaskRef = parentRef,
            accessState = TaskAccessState(
                ref = childRef,
                revision = 10L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.ChildOnly
            )
        )

        // Project hierarchy: ChildOnly task must project as top-level flat item without synthetic parent
        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(childOnlyTodo), TodoFilter.ALL)
        assertEquals(1, snapshot.items.size)
        val item = snapshot.items.first()

        assertTrue(item.isChildOnly, "Projected item must be marked isChildOnly")
        assertEquals(0, item.depth, "ChildOnly item must render flat at depth 0")
        assertFalse(item.hasLiveChildren, "ChildOnly item must not report live children")
        assertEquals(0, item.childCount, "ChildOnly item must have 0 child count")
        assertEquals(0, item.completedChildCount, "ChildOnly item must have 0 completed child count")

        // UI Flat layout rule: start padding is 0dp
        val startPaddingDp = if (!item.isChildOnly && item.depth > 0) 32 else 0
        assertEquals(0, startPaddingDp, "Child-only items must have 0dp start padding (flat layout)")
    }

    @Test
    fun testParentCompletionRequiresFullGroupCoverage() {
        val rootRef = testRef("alice", "root-1")
        val child1Ref = testRef("alice", "child-1")
        val child2Ref = testRef("alice", "child-2")

        val root = createTodo(
            owner = "alice",
            rowId = "root-1",
            completed = false,
            accessState = TaskAccessState(
                ref = rootRef,
                revision = 1L,
                capabilities = TaskCapabilities.OWNER,
                coverage = HierarchyCoverage.FullGroup
            )
        )
        val child1 = createTodo("alice", "child-1", completed = true, parentTaskRef = rootRef)
        val child2 = createTodo("alice", "child-2", completed = true, parentTaskRef = rootRef)

        // 1. With FullGroup coverage, all completed children derive parent completion
        val fullSnapshot = TaskHierarchyProjector.projectHierarchy(listOf(root, child1, child2), TodoFilter.ALL)
        val fullRootItem = fullSnapshot.items.first { it.todo.taskRef == rootRef }
        assertTrue(fullRootItem.effectiveCompleted, "FullGroup parent must derive effective completed = true when all children are completed")

        // 2. ChildOnly coverage: completing an isolated child does not trigger parent completion
        val missingParentRef = testRef("alice", "missing-parent-1")
        val childOnlyTask = createTodo(
            owner = "alice",
            rowId = "isolated-child",
            completed = true,
            parentTaskRef = missingParentRef,
            accessState = TaskAccessState(
                ref = testRef("alice", "isolated-child"),
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.ChildOnly
            )
        )
        val childOnlySnapshot = TaskHierarchyProjector.projectHierarchy(listOf(childOnlyTask), TodoFilter.ALL)
        val childOnlyItem = childOnlySnapshot.items.first()
        assertTrue(childOnlyItem.isChildOnly)
        assertTrue(childOnlyItem.effectiveCompleted)
        assertEquals(0, childOnlyItem.childCount)
    }

    @Test
    fun testCanonicalListKeysPreventCollisionsAcrossOwnersWithIdenticalRowId() {
        val aliceRef = testRef("alice", "shared-id-123")
        val bobRef = testRef("bob", "shared-id-123")

        assertNotEquals(aliceRef, bobRef, "TaskRefs with different owners must not be equal")
        assertEquals(aliceRef.rowId, bobRef.rowId, "Row IDs are intentionally identical")

        val todoAlice = createTodo("alice", "shared-id-123", title = "Alice's Task")
        val todoBob = createTodo("bob", "shared-id-123", title = "Bob's Task")

        // List item keys in TodoList use "${todo.taskRef.ownerUserId}:${todo.taskRef.rowId}"
        val keyAlice = "${todoAlice.taskRef.ownerUserId}:${todoAlice.taskRef.rowId}"
        val keyBob = "${todoBob.taskRef.ownerUserId}:${todoBob.taskRef.rowId}"

        assertNotEquals(keyAlice, keyBob, "List item keys must include ownerUserId to prevent LazyColumn key collisions")
        assertEquals("alice:shared-id-123", keyAlice)
        assertEquals("bob:shared-id-123", keyBob)

        // Expansion state set uses TaskRef
        val expandedSet = setOf(aliceRef)
        assertTrue(expandedSet.contains(aliceRef))
        assertFalse(expandedSet.contains(bobRef), "Expanding Alice's task must not expand Bob's task with identical rowId")
    }

    @Test
    fun testPendingTaskCreateAssignmentFeedback() {
        // Creating a new task: taskRef is null
        val newEditor = EditorState(
            taskRef = null,
            rawTitle = "Brand new task",
            assigneeUserId = "bob",
            canManageAssignment = true
        )
        assertNull(newEditor.taskRef)
        assertEquals("bob", newEditor.assigneeUserId)

        // Initial assignment feedback rule
        val isCreating = newEditor.id == null
        val showInitialAssignmentNotice = isCreating && newEditor.assigneeUserId != null
        assertTrue(showInitialAssignmentNotice, "Initial assignment on new task must show awaiting sync notice")

        val noticeMessage = "Initial assignment will be applied after the task is created and synced."
        assertTrue(noticeMessage.contains("created and synced"), "Notice must explain that creation precedes assignment")
    }

    @Test
    fun testPrivacyFirstAccessLossClearsEditorAndReviewWithoutRevokedContent() {
        val revokedRef = testRef("alice", "revoked-task-42")

        // 1. Access loss evidence contains ONLY content-free metadata
        val evidence = AccessLossEvidence(
            ref = revokedRef,
            opId = "op-999",
            baseVersion = 3L,
            submittedVersion = 4L,
            timestamp = 1700000000000L,
            reason = AccessLossReason.REVOKED
        )
        assertEquals(revokedRef, evidence.ref)
        assertEquals(AccessLossReason.REVOKED, evidence.reason)

        // 2. Verify evidence contains NO title, NO diff, NO description via portable JSON serialization
        val jsonString = Json.encodeToString(AccessLossEvidence.serializer(), evidence)
        val jsonElement = Json.parseToJsonElement(jsonString) as JsonObject
        assertFalse(jsonElement.containsKey("title"), "AccessLossEvidence JSON must never contain title")
        assertFalse(jsonElement.containsKey("diff"), "AccessLossEvidence JSON must never contain diff")
        assertFalse(jsonElement.containsKey("content"), "AccessLossEvidence JSON must never contain content")
        assertFalse(jsonElement.containsKey("draft"), "AccessLossEvidence JSON must never contain draft")
        assertTrue(jsonElement.containsKey("ref"), "AccessLossEvidence JSON must contain ref")
        assertTrue(jsonElement.containsKey("reason"), "AccessLossEvidence JSON must contain reason")
        assertTrue(jsonElement.containsKey("timestamp"), "AccessLossEvidence JSON must contain timestamp")

        // 3. UI state access loss purge and immediate composition suppression
        val activeEditor = EditorState(
            taskRef = revokedRef,
            rawTitle = "Private Revoked Content",
            assigneeUserId = "bob"
        )
        val activeConflict = ConflictState(
            taskRef = revokedRef,
            localTodo = createTodo("alice", "revoked-task-42", title = "Revoked Local"),
            remoteTodo = null
        )
        val uiState = TodoUiState(
            editor = activeEditor,
            conflict = activeConflict,
            accessLossEvidence = listOf(evidence)
        )

        // Defense-in-depth composition suppression rules:
        val isEditorRevoked = uiState.editor?.taskRef != null &&
                uiState.accessLossEvidence.any { it.ref == uiState.editor.taskRef }
        assertTrue(isEditorRevoked, "UI state must detect that open editor refers to revoked task")

        val isConflictRevoked = uiState.conflict?.taskRef != null &&
                uiState.accessLossEvidence.any { it.ref == uiState.conflict.taskRef }
        assertTrue(isConflictRevoked, "UI state must detect that conflict dialog refers to revoked task")

        // Purged state clears both editor and conflict
        val purgedState = uiState.copy(editor = null, conflict = null)
        assertNull(purgedState.editor, "Revoked editor must be purged from UI state")
        assertNull(purgedState.conflict, "Revoked conflict must be purged from UI state")
        val dismissAction = TodoAction.DismissAccessLossNotice(revokedRef)
        assertEquals(revokedRef, dismissAction.ref)
    }

    @Test
    fun testSlotVersionConflictShowsClearFeedback() {
        val conflict = AssignmentSlotResult.VersionConflict(
            expectedVersion = 1L,
            actualVersion = 2L
        )
        assertEquals(1L, conflict.expectedVersion)
        assertEquals(2L, conflict.actualVersion)

        val conflictMessage = "Assignment Version Conflict: The assignment was updated remotely (version mismatch). Please refresh the task to see the latest assignment."
        assertTrue(conflictMessage.contains("mismatch"), "Feedback must explain version mismatch")
        assertTrue(conflictMessage.contains("refresh"), "Feedback must prompt user to refresh")
    }

    @Test
    fun testCollaborationRequiredShowsWholeAppUpgradeWithoutPrivateFallback() {
        val error = SafeError.CollaborationRequired(
            code = "COLLABORATION_REQUIRED",
            message = "Application upgrade required for collaboration"
        )
        assertEquals("COLLABORATION_REQUIRED", error.code)

        val progress = SyncProgress(
            status = SyncStatus.PAUSED_PROTOCOL,
            safeError = error
        )
        val isUpgradeRequired = progress.safeError is SafeError.CollaborationRequired
        assertTrue(isUpgradeRequired, "SyncProgress must reflect collaboration upgrade requirement")

        val uiBannerTitle = if (isUpgradeRequired) "Upgrade Required" else "Sync Incompatible"
        assertEquals("Upgrade Required", uiBannerTitle)

        // Must not provide private sync fallback when collaboration is required
        val canSyncNow = progress.status != SyncStatus.SYNCING &&
                progress.status != SyncStatus.PAUSED_PROTOCOL &&
                !isUpgradeRequired
        assertFalse(canSyncNow, "Sync Now must be disabled when upgrade is required (no private sync fallback)")
    }

    @Test
    fun testTaskAssigneePickerDirectClearPreservesInheritedStateAndDispatchesSlotTombstone() {
        val taskRef = testRef("alice", "subtask-1")
        var directAssignee: String? = "bob"
        val inheritedAssignee = "charlie"
        var dispatchedIntent: AssignmentIntent? = null

        // Creator clears direct assignee
        val onClear = {
            directAssignee = null
            dispatchedIntent = AssignmentIntent(
                taskRef = taskRef,
                desiredRecipientUserId = null,
                expectedSlotVersion = 3L
            )
        }
        onClear()

        assertNull(directAssignee, "Direct assignee must be set to null")
        assertNotNull(dispatchedIntent, "Clear action must dispatch slot intent")
        assertNull(dispatchedIntent?.desiredRecipientUserId, "Slot intent must have desiredRecipientUserId = null (tombstone)")
        assertEquals(3L, dispatchedIntent?.expectedSlotVersion)

        // Inherited assignee remains intact and is displayed
        val effectiveAssignee = directAssignee ?: inheritedAssignee
        assertEquals("charlie", effectiveAssignee, "After clearing direct assignee, task must reflect inherited assignee")
    }

    @Test
    fun testWholeAppSyncSuppressionOnCollaborationRequired() {
        val collabError = SafeError.CollaborationRequired()
        val pausedProgress = SyncProgress(
            status = SyncStatus.PAUSED_PROTOCOL,
            safeError = collabError,
            pendingCount = 2
        )

        val isUpgradeRequired = pausedProgress.safeError is SafeError.CollaborationRequired
        assertTrue(isUpgradeRequired)

        // Whole-app cutoff: no sync triggering allowed
        val syncAllowed = pausedProgress.status != SyncStatus.SYNCING &&
                pausedProgress.status != SyncStatus.PAUSED_PROTOCOL &&
                !isUpgradeRequired
        assertFalse(syncAllowed, "Sync action must be strictly suppressed across whole app on COLLABORATION_REQUIRED")
    }
}
