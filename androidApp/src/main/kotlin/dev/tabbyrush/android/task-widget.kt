package dev.tabbyrush.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.tabbyrush.shared.contract.DeadlinePresentation
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.TaskDeadlinePresentation
import dev.tabbyrush.shared.contract.TaskHierarchyProjector
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation projection state displayed by TabbyRushTaskWidget.
 * Ensures non-authenticated, locked, and error states reveal zero private task titles.
 */
sealed interface WidgetDisplayState {
    data object Loading : WidgetDisplayState
    data object SignedOut : WidgetDisplayState
    data object Locked : WidgetDisplayState
    data object Unavailable : WidgetDisplayState
    data class Content(
        val token: SurfaceAccountToken,
        val sections: TaskSections
    ) : WidgetDisplayState
}

/**
 * Production GlanceAppWidget implementing a responsive, single scrollable task list
 * partitioned into Incomplete and Completed sections.
 */
class TabbyRushTaskWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 120.dp), // 2x2 small
            DpSize(240.dp, 120.dp), // 3x2 wide
            DpSize(240.dp, 240.dp), // 3x3 medium
            DpSize(320.dp, 360.dp)  // 4x4+ large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
        if (hostOwner != null && _displayState.value is WidgetDisplayState.Loading) {
            // Route initial hydration strictly through the fenced TaskWidgetUpdater
            TaskWidgetUpdater.refreshAll(context, hostOwner.taskSurfaces)
        }

        provideContent {
            GlanceTheme {
                val state by _displayState.collectAsState()
                WidgetRoot(state = state)
            }
        }
    }

    companion object {
        private val _displayState = MutableStateFlow<WidgetDisplayState>(WidgetDisplayState.Loading)
        val displayState: StateFlow<WidgetDisplayState> = _displayState.asStateFlow()

        fun updateDisplayState(newState: WidgetDisplayState) {
            _displayState.value = newState
        }

        fun clearState() {
            _displayState.value = WidgetDisplayState.SignedOut
        }
    }
}

@Composable
private fun WidgetRoot(state: WidgetDisplayState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val isCompact = size.width < 180.dp || size.height < 180.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        when (state) {
            WidgetDisplayState.Loading -> {
                WidgetPlaceholder(
                    title = context.getString(R.string.task_widget_title),
                    subtitle = context.getString(R.string.task_widget_loading)
                )
            }
            WidgetDisplayState.SignedOut -> {
                WidgetPlaceholderWithAction(
                    title = context.getString(R.string.task_widget_title),
                    subtitle = context.getString(R.string.task_widget_signed_out),
                    buttonText = context.getString(R.string.task_widget_open_app),
                    onClick = actionStartActivity(createOpenAppIntent(context))
                )
            }
            WidgetDisplayState.Locked -> {
                WidgetPlaceholder(
                    title = context.getString(R.string.task_widget_title),
                    subtitle = context.getString(R.string.task_widget_locked)
                )
            }
            WidgetDisplayState.Unavailable -> {
                WidgetPlaceholderWithAction(
                    title = context.getString(R.string.task_widget_title),
                    subtitle = context.getString(R.string.task_widget_unavailable),
                    buttonText = context.getString(R.string.task_widget_open_app),
                    onClick = actionStartActivity(createOpenAppIntent(context))
                )
            }
            is WidgetDisplayState.Content -> {
                WidgetTaskList(
                    token = state.token,
                    sections = state.sections,
                    isCompact = isCompact,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun WidgetPlaceholder(title: String, subtitle: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        )
    }
}

@Composable
private fun WidgetPlaceholderWithAction(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: androidx.glance.action.Action
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Box(
            modifier = GlanceModifier
                .height(48.dp) // Minimum 48dp accessible tap target
                .background(GlanceTheme.colors.primary)
                .cornerRadius(8.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(onClick)
                .semantics { contentDescription = buttonText }
        ) {
            Text(
                text = buttonText,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun WidgetTaskList(
    token: SurfaceAccountToken,
    sections: TaskSections,
    isCompact: Boolean,
    context: Context
) {
    val nowEpochMs = System.currentTimeMillis()
    val incompleteItems = projectWidgetItems(sections.incomplete, nowEpochMs)
    val completedItems = projectWidgetItems(sections.completed, nowEpochMs)
    val totalCount = incompleteItems.size + completedItems.size

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Top Header
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.task_widget_title),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (isCompact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
                    .clickable(actionStartActivity(createOpenAppIntent(context)))
            )

            // Add Task Button with >=48dp accessible tap target
            Box(
                modifier = GlanceModifier
                    .height(48.dp) // Accessible 48dp touch target
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clickable(actionStartActivity(createAddTaskIntent(context, token)))
                    .semantics { contentDescription = context.getString(R.string.task_widget_add_task) }
            ) {
                Text(
                    text = "+ " + context.getString(R.string.task_widget_add_task),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Single Scrollable Task List
        if (totalCount == 0) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.task_widget_empty),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                // 1. Incomplete Section
                if (incompleteItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = context.getString(
                                R.string.task_widget_incomplete_header,
                                incompleteItems.size
                            )
                        )
                    }
                    items(
                        items = incompleteItems,
                        itemId = { it.todo.id.hashCode().toLong() }
                    ) { item ->
                        TaskRow(
                            token = token,
                            item = item,
                            isCompact = isCompact,
                            context = context
                        )
                    }
                }

                // 2. Completed Section
                if (completedItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = context.getString(
                                R.string.task_widget_completed_header,
                                completedItems.size
                            )
                        )
                    }
                    items(
                        items = completedItems,
                        itemId = { it.todo.id.hashCode().toLong() }
                    ) { item ->
                        TaskRow(
                            token = token,
                            item = item,
                            isCompact = isCompact,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun TaskRow(
    token: SurfaceAccountToken,
    item: WidgetTaskItem,
    isCompact: Boolean,
    context: Context
) {
    val todo = item.todo
    val isCompleted = item.effectiveCompleted
    val startPadding = if (item.isChild) (if (isCompact) 12.dp else 20.dp) else 0.dp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.hasLiveChildren) {
            CheckBox(
                checked = isCompleted,
                onCheckedChange = null,
                modifier = GlanceModifier
                    .width(48.dp)
                    .height(48.dp)
                    .semantics {
                        contentDescription = context.getString(
                            R.string.task_widget_parent_automatic_content_desc,
                            todo.title,
                            item.completedChildCount,
                            item.childCount
                        )
                    }
            )
        } else {
            val descriptionRes = if (item.isChild) {
                if (isCompleted) R.string.task_widget_child_checkbox_completed
                else R.string.task_widget_child_checkbox_incomplete
            } else {
                if (isCompleted) R.string.task_widget_checkbox_completed
                else R.string.task_widget_checkbox_incomplete
            }
            CheckBox(
                checked = isCompleted,
                onCheckedChange = actionRunCallback<ToggleTaskActionCallback>(
                    TaskWidgetActionKeys.toggleTaskParameters(
                        token = token,
                        taskId = todo.id,
                        desiredCompleted = !isCompleted
                    )
                ),
                modifier = GlanceModifier
                    .width(48.dp)
                    .height(48.dp)
                    .semantics {
                        contentDescription = context.getString(descriptionRes, todo.title)
                    }
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))

        val subtasksProgress = if (item.hasLiveChildren) {
            context.getString(
                R.string.task_widget_subtasks_progress,
                item.completedChildCount,
                item.childCount
            )
        } else null

        val deadlinePres = item.deadlinePresentation
        val rowContentDescription = buildString {
            append(todo.title)
            if (subtasksProgress != null) {
                append(". ")
                append(subtasksProgress)
            }
            if (deadlinePres != null) {
                append(". ")
                append(deadlinePres.contentDescription)
            }
        }

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(createOpenTaskIntent(context, token, todo.id)))
                .semantics { contentDescription = rowContentDescription },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = todo.title,
                maxLines = if (isCompact) 1 else 2,
                style = TextStyle(
                    color = if (isCompleted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
            )

            if (subtasksProgress != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = subtasksProgress,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            if (deadlinePres != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                val deadlineText = if (deadlinePres.isOverdue) {
                    context.getString(
                        R.string.task_widget_deadline_with_overdue,
                        deadlinePres.label,
                        context.getString(R.string.task_widget_overdue)
                    )
                } else {
                    deadlinePres.label
                }
                Text(
                    text = deadlineText,
                    maxLines = 3,
                    style = TextStyle(
                        color = if (deadlinePres.isOverdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (deadlinePres.isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }
    }
}
