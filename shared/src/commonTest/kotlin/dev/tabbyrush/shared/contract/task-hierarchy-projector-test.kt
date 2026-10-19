package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskHierarchyProjectorTest {

    @Test
    fun testEncounterOrderAndDepth() {
        val r1 = Todo(id = "root-1", title = "Root 1", completed = false, createdAt = 1000L)
        val r2 = Todo(id = "root-2", title = "Root 2", completed = false, createdAt = 2000L)
        val c1 = Todo(id = "child-1", title = "Child 1", completed = false, createdAt = 1100L, parentTaskId = "root-1")
        val c2 = Todo(id = "child-2", title = "Child 2", completed = false, createdAt = 1200L, parentTaskId = "root-1")

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(r1, r2, c1, c2), TodoFilter.ALL)
        val items = snapshot.items

        // Encounter order for roots: root-2 (2000L), then root-1 (1000L)
        // Under root-1: child-2 (1200L), then child-1 (1100L)
        assertEquals(4, items.size)
        assertEquals("root-2", items[0].todo.id)
        assertEquals(0, items[0].depth)
        assertFalse(items[0].hasLiveChildren)

        assertEquals("root-1", items[1].todo.id)
        assertEquals(0, items[1].depth)
        assertTrue(items[1].hasLiveChildren)
        assertEquals(2, items[1].childCount)

        assertEquals("child-2", items[2].todo.id)
        assertEquals(1, items[2].depth)
        assertFalse(items[2].hasLiveChildren)

        assertEquals("child-1", items[3].todo.id)
        assertEquals(1, items[3].depth)
        assertFalse(items[3].hasLiveChildren)
    }

    @Test
    fun testEffectiveCompletionDerivation() {
        val rootId = "root-1"
        // Root has raw completed = false, but both children completed -> effective = true
        val root = Todo(id = rootId, title = "Root", completed = false, createdAt = 1000L)
        val c1 = Todo(id = "c1", title = "C1", completed = true, createdAt = 1100L, parentTaskId = rootId)
        val c2 = Todo(id = "c2", title = "C2", completed = true, createdAt = 1200L, parentTaskId = rootId)

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(root, c1, c2), TodoFilter.ALL)
        val rootItem = snapshot.items.first { it.todo.id == rootId }
        assertTrue(rootItem.effectiveCompleted, "Parent with all completed children must be effectively completed")

        // Add one incomplete child -> parent becomes effectively incomplete
        val c3 = Todo(id = "c3", title = "C3", completed = false, createdAt = 1300L, parentTaskId = rootId)
        val snapshot2 = TaskHierarchyProjector.projectHierarchy(listOf(root, c1, c2, c3), TodoFilter.ALL)
        val rootItem2 = snapshot2.items.first { it.todo.id == rootId }
        assertFalse(rootItem2.effectiveCompleted, "Parent with any active child must be effectively active")
    }

    @Test
    fun testStandaloneRootEffectiveCompletion() {
        val activeRoot = Todo(id = "r-active", title = "Active Root", completed = false, createdAt = 1000L)
        val completedRoot = Todo(id = "r-comp", title = "Completed Root", completed = true, createdAt = 2000L)

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(activeRoot, completedRoot), TodoFilter.ALL)
        val activeItem = snapshot.items.first { it.todo.id == "r-active" }
        val completedItem = snapshot.items.first { it.todo.id == "r-comp" }

        assertFalse(activeItem.effectiveCompleted)
        assertTrue(completedItem.effectiveCompleted)
    }

    @Test
    fun testFilteringAndContextOnlyParent() {
        // Mixed group: Root R has active C1 and completed C2. R is effectively active.
        val rId = "r1"
        val root = Todo(id = rId, title = "Parent", completed = false, createdAt = 1000L)
        val c1Active = Todo(id = "c1", title = "Child 1 Active", completed = false, createdAt = 1100L, parentTaskId = rId)
        val c2Completed = Todo(id = "c2", title = "Child 2 Completed", completed = true, createdAt = 1200L, parentTaskId = rId)
        val standaloneCompleted = Todo(id = "r-done", title = "Standalone Done", completed = true, createdAt = 900L)

        val allTodos = listOf(root, c1Active, c2Completed, standaloneCompleted)

        // 1. ACTIVE filter:
        // Root matches directly (effectively active). C1 matches directly. C2 is omitted.
        val activeSnapshot = TaskHierarchyProjector.projectHierarchy(allTodos, TodoFilter.ACTIVE)
        val activeIds = activeSnapshot.items.map { it.todo.id }
        assertEquals(listOf("r1", "c1"), activeIds)
        assertFalse(activeSnapshot.items[0].isContextOnly, "Root matches directly in active filter")
        assertFalse(activeSnapshot.items[1].isContextOnly)

        // 2. COMPLETED filter:
        // C2 matches directly. Root R appears as context-only! C1 is omitted. Standalone done matches directly.
        val completedSnapshot = TaskHierarchyProjector.projectHierarchy(allTodos, TodoFilter.COMPLETED)
        val completedIds = completedSnapshot.items.map { it.todo.id }
        assertEquals(listOf("r1", "c2", "r-done"), completedIds)

        val rootContextItem = completedSnapshot.items.first { it.todo.id == "r1" }
        assertTrue(rootContextItem.isContextOnly, "Root must appear as context-only in completed filter")
        assertFalse(completedSnapshot.items.first { it.todo.id == "c2" }.isContextOnly)
        assertFalse(completedSnapshot.items.first { it.todo.id == "r-done" }.isContextOnly)
    }

    @Test
    fun testMetricsCountsExcludesContextRows() {
        // Root R has active C1 and completed C2. Standalone completed R2.
        // Real tasks: R (active), C1 (active), C2 (completed), R2 (completed) -> total = 4, active = 2, completed = 2
        val rId = "r1"
        val root = Todo(id = rId, title = "Parent", completed = false, createdAt = 1000L)
        val c1Active = Todo(id = "c1", title = "Child 1 Active", completed = false, createdAt = 1100L, parentTaskId = rId)
        val c2Completed = Todo(id = "c2", title = "Child 2 Completed", completed = true, createdAt = 1200L, parentTaskId = rId)
        val standaloneCompleted = Todo(id = "r-done", title = "Standalone Done", completed = true, createdAt = 900L)

        val allTodos = listOf(root, c1Active, c2Completed, standaloneCompleted)

        val counts = TaskHierarchyProjector.computeCounts(allTodos)
        assertEquals(4, counts.all)
        assertEquals(2, counts.active)
        assertEquals(2, counts.completed)

        val completedSnapshot = TaskHierarchyProjector.projectHierarchy(allTodos, TodoFilter.COMPLETED)
        // In COMPLETED snapshot, items has 3 elements (R1 context, C2, R-done), but counts must still be accurate!
        assertEquals(4, completedSnapshot.counts.all)
        assertEquals(2, completedSnapshot.counts.active)
        assertEquals(2, completedSnapshot.counts.completed)
    }

    @Test
    fun testAncestorDeletionDominance() {
        val rootId = "deleted-root"
        val deletedRoot = Todo(id = rootId, title = "Deleted Root", completed = false, createdAt = 1000L, isDeleted = true)
        val child = Todo(id = "child", title = "Child", completed = false, createdAt = 1100L, parentTaskId = rootId)

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(deletedRoot, child), TodoFilter.ALL)
        assertTrue(snapshot.items.isEmpty(), "Child of deleted root must be suppressed")
        assertEquals(0, snapshot.counts.all)
    }

    @Test
    fun testMissingParentHandling() {
        val orphan = Todo(id = "orphan", title = "Orphan Child", completed = false, createdAt = 1000L, parentTaskId = "non-existent")

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(orphan), TodoFilter.ALL)
        assertTrue(snapshot.items.isEmpty(), "Orphan child with missing parent must not be in normal items")
        assertEquals(1, snapshot.unresolvedChildren.size)
        assertEquals("orphan", snapshot.unresolvedChildren[0].id)
        assertEquals(0, snapshot.counts.all)
    }

    @Test
    fun testCompactSurfaceSectionsKeepsGroupsIntact() {
        // Mixed group: R1 (incomplete root) + C1 (active) + C2 (completed)
        // Fully complete group: R2 + C3 (completed)
        // Standalone completed: R3
        val r1 = Todo(id = "r1", title = "R1", completed = false, createdAt = 3000L)
        val c1 = Todo(id = "c1", title = "C1", completed = false, createdAt = 3100L, parentTaskId = "r1")
        val c2 = Todo(id = "c2", title = "C2", completed = true, createdAt = 3200L, parentTaskId = "r1")

        val r2 = Todo(id = "r2", title = "R2", completed = false, createdAt = 2000L)
        val c3 = Todo(id = "c3", title = "C3", completed = true, createdAt = 2100L, parentTaskId = "r2")

        val r3 = Todo(id = "r3", title = "R3", completed = true, createdAt = 1000L)

        val sections = TaskHierarchyProjector.partitionCompactSections(listOf(r1, c1, c2, r2, c3, r3))

        // Incomplete section must contain R1 group (R1, C2, C1 in encounter order)
        val incompleteIds = sections.incomplete.map { it.id }
        assertEquals(listOf("r1", "c2", "c1"), incompleteIds, "Mixed group must stay intact in compact Active")

        // Completed section must contain R2 group (R2, C3) and standalone R3
        val completedIds = sections.completed.map { it.id }
        assertEquals(listOf("r2", "c3", "r3"), completedIds, "Fully complete group must stay intact in compact Completed")
    }
}
