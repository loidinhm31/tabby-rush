package dev.tabbyrush.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.TaskRef
/**
 * Immutable parameters and callback handling for Glance widget interactions.
 */
object TaskWidgetActionKeys {
    val PARAM_ACCOUNT_KEY = ActionParameters.Key<String>("account_key")
    val PARAM_GENERATION = ActionParameters.Key<Long>("generation")
    val PARAM_OWNER_USER_ID = ActionParameters.Key<String>("owner_user_id")
    val PARAM_ROW_ID = ActionParameters.Key<String>("row_id")
    val PARAM_TASK_ID = ActionParameters.Key<String>("task_id")
    val PARAM_DESIRED_COMPLETED = ActionParameters.Key<Boolean>("desired_completed")

    const val ACTION_WIDGET_VIEW_TASK = "dev.tabbyrush.android.ACTION_VIEW_TASK"
    const val ACTION_WIDGET_ADD_TASK = "dev.tabbyrush.android.ACTION_ADD_TASK"
    const val EXTRA_ACCOUNT_KEY = "extra_account_key"
    const val EXTRA_GENERATION = "extra_generation"
    const val EXTRA_OWNER_USER_ID = "extra_owner_user_id"
    const val EXTRA_ROW_ID = "extra_row_id"
    const val EXTRA_TASK_ID = "extra_task_id"
    private const val TAG = "TaskWidgetActions"

    fun toggleTaskParameters(
        token: SurfaceAccountToken,
        taskRef: TaskRef,
        desiredCompleted: Boolean
    ): ActionParameters {
        return actionParametersOf(
            PARAM_ACCOUNT_KEY to token.accountKey.value,
            PARAM_GENERATION to token.generation,
            PARAM_OWNER_USER_ID to taskRef.ownerUserId,
            PARAM_ROW_ID to taskRef.rowId,
            PARAM_TASK_ID to taskRef.rowId,
            PARAM_DESIRED_COMPLETED to desiredCompleted
        )
    }

    fun getOpenTaskRequestCode(token: SurfaceAccountToken, taskRef: TaskRef): Int {
        return (token.accountKey.value + ":" + token.generation + ":" + taskRef.ownerUserId + ":" + taskRef.rowId + ":open").hashCode() and 0x7FFFFFFF
    }

    fun getAddTaskRequestCode(token: SurfaceAccountToken): Int {
        return (token.accountKey.value + ":" + token.generation + ":add").hashCode() and 0x7FFFFFFF
    }

    fun getOpenAppRequestCode(): Int = 0x5441534B // "TASK"
}

/**
 * ActionCallback executed by the Glance runtime when a task checkbox is tapped.
 * Deserializes immutable desired-state parameters, verifies runtime readiness against the token,
 * commits completion atomically via TaskSurfaceAccess, and triggers surface refresh.
 */
class ToggleTaskActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val accountKeyStr = parameters[TaskWidgetActionKeys.PARAM_ACCOUNT_KEY]
        val generation = parameters[TaskWidgetActionKeys.PARAM_GENERATION]
        val ownerUserId = parameters[TaskWidgetActionKeys.PARAM_OWNER_USER_ID]
        val rowId = parameters[TaskWidgetActionKeys.PARAM_ROW_ID] ?: parameters[TaskWidgetActionKeys.PARAM_TASK_ID]
        val desiredCompleted = parameters[TaskWidgetActionKeys.PARAM_DESIRED_COMPLETED]

        if (accountKeyStr.isNullOrBlank() || generation == null || ownerUserId.isNullOrBlank() || rowId.isNullOrBlank() || desiredCompleted == null) {
            Log.w(TAG, "Rejecting malformed or legacy owner-less widget action parameters: $parameters")
            return
        }
        val taskRef = TaskRef(ownerUserId, rowId)

        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
        if (hostOwner == null) {
            Log.w(TAG, "Cannot dispatch widget completion: Application is not a TaskSurfaceHostOwner")
            return
        }

        val host = hostOwner.taskSurfaces
        val token = SurfaceAccountToken(AccountKey(accountKeyStr), generation)
        val nowEpochMs = System.currentTimeMillis()

        when (val result = host.access.setCompleted(token, taskRef, desiredCompleted, nowEpochMs)) {
            is SurfaceResult.Value -> {
                Log.d(TAG, "Widget setCompleted succeeded for task $taskRef (change: ${result.value})")
                host.refresh(SurfaceRefreshReason.Widget)
                TaskWidgetUpdater.refreshAll(context, host)
            }
            SurfaceResult.Rejected -> {
                Log.w(TAG, "Widget setCompleted rejected for token $token (stale generation or closed admission)")
                host.refresh(SurfaceRefreshReason.Widget)
                TaskWidgetUpdater.refreshAll(context, host)
            }
            SurfaceResult.Unavailable -> {
                Log.w(TAG, "Widget setCompleted unavailable (storage locked or restoring)")
            }
        }
    }

    companion object {
        private const val TAG = "ToggleTaskAction"
    }
}

/**
 * Creates an explicit Intent targeting MainActivity to open and view a specific task.
 */
fun createOpenTaskIntent(context: Context, token: SurfaceAccountToken, taskRef: TaskRef): Intent {
    return Intent(context, MainActivity::class.java).apply {
        action = TaskWidgetActionKeys.ACTION_WIDGET_VIEW_TASK
        data = Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-widget")
            .appendPath("view")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskRef.ownerUserId)
            .appendPath(taskRef.rowId)
            .build()
        putExtra(TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY, token.accountKey.value)
        putExtra(TaskWidgetActionKeys.EXTRA_GENERATION, token.generation)
        putExtra(TaskWidgetActionKeys.EXTRA_OWNER_USER_ID, taskRef.ownerUserId)
        putExtra(TaskWidgetActionKeys.EXTRA_ROW_ID, taskRef.rowId)
        putExtra(TaskWidgetActionKeys.EXTRA_TASK_ID, taskRef.rowId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}

/**
 * Creates an immutable PendingIntent targeting MainActivity to open and view a specific task.
 */
fun createOpenTaskPendingIntent(context: Context, token: SurfaceAccountToken, taskRef: TaskRef): PendingIntent {
    val intent = createOpenTaskIntent(context, token, taskRef)
    val requestCode = TaskWidgetActionKeys.getOpenTaskRequestCode(token, taskRef)
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/**
 * Creates an explicit Intent targeting MainActivity to initiate task creation in the active account.
 */
fun createAddTaskIntent(context: Context, token: SurfaceAccountToken): Intent {
    return Intent(context, MainActivity::class.java).apply {
        action = TaskWidgetActionKeys.ACTION_WIDGET_ADD_TASK
        data = Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-widget")
            .appendPath("add")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .build()
        putExtra(TaskWidgetActionKeys.EXTRA_GENERATION, token.generation)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}

/**
 * Creates an immutable PendingIntent targeting MainActivity to initiate task creation.
 */
fun createAddTaskPendingIntent(context: Context, token: SurfaceAccountToken): PendingIntent {
    val intent = createAddTaskIntent(context, token)
    val requestCode = TaskWidgetActionKeys.getAddTaskRequestCode(token)
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/**
 * Creates an explicit Intent targeting MainActivity for unauthenticated or generic app launch.
 */
fun createOpenAppIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        data = Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-widget")
            .appendPath("main")
            .build()
    }
}

/**
 * Creates an immutable PendingIntent targeting MainActivity for unauthenticated or generic app launch.
 */
fun createOpenAppPendingIntent(context: Context): PendingIntent {
    val intent = createOpenAppIntent(context)
    return PendingIntent.getActivity(
        context,
        TaskWidgetActionKeys.getOpenAppRequestCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
