package dev.tabbyrush.shared.contract

/**
 * Representation of a task in the projected hierarchy.
 * Preserves the raw [todo] (with its stored completed value) while exposing
 * projected [effectiveCompleted], hierarchy depth, live children count,
 * and whether this item is displayed as parent context in a filtered view.
 */
data class HierarchicalTodoItem(
    val todo: Todo,
    val effectiveCompleted: Boolean,
    val hasLiveChildren: Boolean,
    val isContextOnly: Boolean = false,
    val depth: Int = 0,
    val childCount: Int = 0,
    val completedChildCount: Int = 0,
    val isChildOnly: Boolean = false
)

data class GroupedTask(
    val root: Todo,
    val effectiveCompleted: Boolean,
    val children: List<Todo>,
    val isChildOnly: Boolean = false
)

data class ProjectedTaskSnapshot(
    val items: List<HierarchicalTodoItem>,
    val counts: TodoCounts,
    val unresolvedChildren: List<Todo> = emptyList()
)

object TaskHierarchyProjector {

    private val ENCOUNTER_COMPARATOR = compareByDescending<Todo> { it.createdAt }
        .thenByDescending { it.taskRef.ownerUserId }
        .thenByDescending { it.taskRef.rowId }
    /**
     * Projects the complete todo snapshot into an indexed hierarchical structure,
     * applying encounter ordering, ancestor deletion dominance, effective completion derivation,
     * filter-specific context inclusion, and accurate counts from unique real tasks.
     */
    fun projectHierarchy(
        todos: List<Todo>,
        filter: TodoFilter
    ): ProjectedTaskSnapshot {
        val groups = buildGroups(todos)
        val validGroups = groups.validGroups
        val unresolvedChildren = groups.unresolvedChildren

        val counts = computeCountsFromGroups(validGroups)
        val items = filterAndFlatten(validGroups, filter)

        return ProjectedTaskSnapshot(
            items = items,
            counts = counts,
            unresolvedChildren = unresolvedChildren
        )
    }

    /**
     * Computes metrics strictly counting each valid non-deleted real task once
     * using its effective completion state. Context rows do not inflate these counts.
     */
    fun computeCounts(todos: List<Todo>): TodoCounts {
        val groups = buildGroups(todos).validGroups
        return computeCountsFromGroups(groups)
    }

    /**
     * Partitions todos into compact surface sections (incomplete vs completed).
     * Compact Active keeps mixed groups with ALL children intact (including completed children).
     * Completed contains standalone completed roots and fully complete groups only.
     * No group is ever split across compact sections.
     */
    fun partitionCompactSections(todos: List<Todo>): TaskSections {
        val groups = buildGroups(todos).validGroups
        val incompleteList = mutableListOf<Todo>()
        val completedList = mutableListOf<Todo>()

        for (group in groups) {
            val destination = if (group.effectiveCompleted) completedList else incompleteList
            destination.add(group.root)
            destination.addAll(group.children)
        }

        return TaskSections(incomplete = incompleteList, completed = completedList)
    }

    private data class GroupBuildResult(
        val validGroups: List<GroupedTask>,
        val unresolvedChildren: List<Todo>
    )

    private fun buildGroups(todos: List<Todo>): GroupBuildResult {
        val rootsByRef = HashMap<TaskRef, Todo>()
        val deletedRootRefs = HashSet<TaskRef>()
        val childrenByParentRef = LinkedHashMap<TaskRef, MutableList<Todo>>()
        val candidateRoots = mutableListOf<Todo>()
        val allChildren = mutableListOf<Todo>()
        val childOnlyTasks = mutableListOf<Todo>()

        // 1. Separate roots and children, index roots
        for (todo in todos) {
            if (todo.parentTaskRef == null) {
                if (todo.isDeleted) {
                    deletedRootRefs.add(todo.taskRef)
                } else {
                    rootsByRef[todo.taskRef] = todo
                    candidateRoots.add(todo)
                }
            } else {
                if (!todo.isDeleted) {
                    if (todo.accessState?.coverage == HierarchyCoverage.ChildOnly && rootsByRef[todo.parentTaskRef] == null && !deletedRootRefs.contains(todo.parentTaskRef)) {
                        // Trusted server metadata marks this child as ChildOnly:
                        // Projected as flat item without requiring parent locally.
                        childOnlyTasks.add(todo)
                    } else {
                        allChildren.add(todo)
                    }
                }
            }
        }

        val unresolvedChildren = mutableListOf<Todo>()

        // 2. Associate children with live roots, applying ancestor deletion dominance
        for (child in allChildren) {
            val parentRef = child.parentTaskRef!!
            if (deletedRootRefs.contains(parentRef)) {
                // Ancestor deletion dominance: child is suppressed
                continue
            }
            if (!rootsByRef.containsKey(parentRef)) {
                // Parent is missing without a confirmed tombstone
                unresolvedChildren.add(child)
                continue
            }
            childrenByParentRef.getOrPut(parentRef) { mutableListOf() }.add(child)
        }

        // Sort children encounter order
        for (childrenList in childrenByParentRef.values) {
            childrenList.sortWith(ENCOUNTER_COMPARATOR)
        }

        // 3. Assemble GroupedTasks in encounter order
        data class TopLevelCandidate(val todo: Todo, val isChildOnly: Boolean)
        val allTopLevel = (candidateRoots.map { TopLevelCandidate(it, isChildOnly = false) } +
                childOnlyTasks.map { TopLevelCandidate(it, isChildOnly = true) })
            .sortedWith(
                compareByDescending<TopLevelCandidate> { it.todo.createdAt }
                    .thenByDescending { it.todo.taskRef.ownerUserId }
                    .thenByDescending { it.todo.taskRef.rowId }
            )

        val validGroups = ArrayList<GroupedTask>(allTopLevel.size)
        for (candidate in allTopLevel) {
            val root = candidate.todo
            if (candidate.isChildOnly) {
                validGroups.add(
                    GroupedTask(
                        root = root,
                        effectiveCompleted = root.completed,
                        children = emptyList(),
                        isChildOnly = true
                    )
                )
            } else {
                val children = childrenByParentRef[root.taskRef] ?: emptyList()
                val isFullGroup = root.accessState == null || root.accessState.coverage == HierarchyCoverage.FullGroup
                val effectiveCompleted = if (isFullGroup && children.isNotEmpty()) {
                    children.all { it.completed }
                } else {
                    root.completed
                }
                validGroups.add(
                    GroupedTask(
                        root = root,
                        effectiveCompleted = effectiveCompleted,
                        children = children,
                        isChildOnly = false
                    )
                )
            }
        }

        unresolvedChildren.sortWith(ENCOUNTER_COMPARATOR)

        return GroupBuildResult(
            validGroups = validGroups,
            unresolvedChildren = unresolvedChildren
        )
    }

    private fun computeCountsFromGroups(groups: List<GroupedTask>): TodoCounts {
        var activeCount = 0
        var completedCount = 0

        for (group in groups) {
            // Count root
            if (group.effectiveCompleted) {
                completedCount++
            } else {
                activeCount++
            }

            // Count children (their effective completion is their raw completion)
            for (child in group.children) {
                if (child.completed) {
                    completedCount++
                } else {
                    activeCount++
                }
            }
        }

        return TodoCounts(
            all = activeCount + completedCount,
            active = activeCount,
            completed = completedCount
        )
    }

    private fun filterAndFlatten(
        groups: List<GroupedTask>,
        filter: TodoFilter
    ): List<HierarchicalTodoItem> {
        val result = mutableListOf<HierarchicalTodoItem>()

        for (group in groups) {
            val root = group.root
            val children = group.children
            val childCount = children.size
            val completedChildCount = children.count { it.completed }
            val rootEffective = group.effectiveCompleted

            when (filter) {
                TodoFilter.ALL -> {
                    result.add(
                        HierarchicalTodoItem(
                            todo = root,
                            effectiveCompleted = rootEffective,
                            hasLiveChildren = childCount > 0,
                            isContextOnly = false,
                            depth = 0,
                            childCount = childCount,
                            completedChildCount = completedChildCount,
                            isChildOnly = group.isChildOnly
                        )
                    )
                    for (child in children) {
                        result.add(
                            HierarchicalTodoItem(
                                todo = child,
                                effectiveCompleted = child.completed,
                                hasLiveChildren = false,
                                isContextOnly = false,
                                depth = 1,
                                childCount = 0,
                                completedChildCount = 0,
                                isChildOnly = false
                            )
                        )
                    }
                }

                TodoFilter.ACTIVE -> {
                    val matchingChildren = children.filter { !it.completed }
                    val rootMatches = !rootEffective

                    if (rootMatches) {
                        result.add(
                            HierarchicalTodoItem(
                                todo = root,
                                effectiveCompleted = rootEffective,
                                hasLiveChildren = childCount > 0,
                                isContextOnly = false,
                                depth = 0,
                                childCount = childCount,
                                completedChildCount = completedChildCount,
                                isChildOnly = group.isChildOnly
                            )
                        )
                        for (child in matchingChildren) {
                            result.add(
                                HierarchicalTodoItem(
                                    todo = child,
                                    effectiveCompleted = false,
                                    hasLiveChildren = false,
                                    isContextOnly = false,
                                    depth = 1,
                                    childCount = 0,
                                    completedChildCount = 0,
                                    isChildOnly = false
                                )
                            )
                        }
                    } else if (matchingChildren.isNotEmpty()) {
                        result.add(
                            HierarchicalTodoItem(
                                todo = root,
                                effectiveCompleted = rootEffective,
                                hasLiveChildren = childCount > 0,
                                isContextOnly = true,
                                depth = 0,
                                childCount = childCount,
                                completedChildCount = completedChildCount,
                                isChildOnly = group.isChildOnly
                            )
                        )
                        for (child in matchingChildren) {
                            result.add(
                                HierarchicalTodoItem(
                                    todo = child,
                                    effectiveCompleted = false,
                                    hasLiveChildren = false,
                                    isContextOnly = false,
                                    depth = 1,
                                    childCount = 0,
                                    completedChildCount = 0,
                                    isChildOnly = false
                                )
                            )
                        }
                    }
                }

                TodoFilter.COMPLETED -> {
                    val matchingChildren = children.filter { it.completed }
                    val rootMatches = rootEffective

                    if (rootMatches) {
                        result.add(
                            HierarchicalTodoItem(
                                todo = root,
                                effectiveCompleted = rootEffective,
                                hasLiveChildren = childCount > 0,
                                isContextOnly = false,
                                depth = 0,
                                childCount = childCount,
                                completedChildCount = completedChildCount,
                                isChildOnly = group.isChildOnly
                            )
                        )
                        for (child in matchingChildren) {
                            result.add(
                                HierarchicalTodoItem(
                                    todo = child,
                                    effectiveCompleted = true,
                                    hasLiveChildren = false,
                                    isContextOnly = false,
                                    depth = 1,
                                    childCount = 0,
                                    completedChildCount = 0,
                                    isChildOnly = false
                                )
                            )
                        }
                    } else if (matchingChildren.isNotEmpty()) {
                        result.add(
                            HierarchicalTodoItem(
                                todo = root,
                                effectiveCompleted = rootEffective,
                                hasLiveChildren = childCount > 0,
                                isContextOnly = true,
                                depth = 0,
                                childCount = childCount,
                                completedChildCount = completedChildCount,
                                isChildOnly = group.isChildOnly
                            )
                        )
                        for (child in matchingChildren) {
                            result.add(
                                HierarchicalTodoItem(
                                    todo = child,
                                    effectiveCompleted = true,
                                    hasLiveChildren = false,
                                    isContextOnly = false,
                                    depth = 1,
                                    childCount = 0,
                                    completedChildCount = 0,
                                    isChildOnly = false
                                )
                            )
                        }
                    }
                }
            }
        }

        return result
    }
}
