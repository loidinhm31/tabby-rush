package dev.tabbyrush.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.DeadlineSelectionKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken

/**
 * Factory utilities for task surface notification explicit intents and immutable PendingIntents.
 * All intents carry account token identity, operation type, and task IDs in both extras and
 * data URIs constructed with Uri.Builder to properly encode delimiter-bearing task IDs and receipts.
 */
object TaskSurfaceIntents {

    const val CHANNEL_ID = "tabbyrush_task_surface_v2"
    const val NOTIFICATION_TAG = "tabbyrush.task.surface"
    const val NOTIFICATION_ID = 4101
    const val GROUP_KEY = "dev.tabbyrush.android.TASK_SURFACE"

    const val ACTION_COMPLETE_SURFACE_TASK = "dev.tabbyrush.android.ACTION_COMPLETE_SURFACE_TASK"
    const val ACTION_DISMISS_SURFACE_NOTIFICATION = "dev.tabbyrush.android.ACTION_DISMISS_SURFACE_NOTIFICATION"
    const val ACTION_VIEW_TASK = "dev.tabbyrush.android.ACTION_VIEW_TASK"

    const val EXTRA_ACCOUNT_KEY = "extra_account_key"
    const val EXTRA_GENERATION = "extra_generation"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_RECEIPT = "extra_receipt"
    const val EXTRA_SELECTION_KEY = "extra_selection_key"

    fun getRequestCode(
        operation: String,
        accountKey: AccountKey,
        generation: Long,
        taskId: String,
        suffix: String = ""
    ): Int {
        var h = operation.hashCode()
        h = 31 * h + accountKey.value.hashCode()
        h = 31 * h + generation.hashCode()
        h = 31 * h + taskId.hashCode()
        if (suffix.isNotEmpty()) {
            h = 31 * h + suffix.hashCode()
        }
        return h and 0x7FFFFFFF
    }

    fun buildCompleteDataUri(token: SurfaceAccountToken, taskId: String): Uri {
        return Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-surface")
            .appendPath("complete")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskId)
            .build()
    }

    fun buildOpenDataUri(token: SurfaceAccountToken, taskId: String): Uri {
        return Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-surface")
            .appendPath("open")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskId)
            .build()
    }

    fun buildDismissDataUri(token: SurfaceAccountToken, taskId: String, receipt: String): Uri {
        return Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-surface")
            .appendPath("dismiss")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskId)
            .appendPath(receipt)
            .build()
    }

    fun createCompleteIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskId: String
    ): Intent {
        return Intent(context, TaskSurfaceActionReceiver::class.java).apply {
            action = ACTION_COMPLETE_SURFACE_TASK
            data = buildCompleteDataUri(token, taskId)
            putExtra(EXTRA_ACCOUNT_KEY, token.accountKey.value)
            putExtra(EXTRA_GENERATION, token.generation)
            putExtra(EXTRA_TASK_ID, taskId)
        }
    }

    fun createCompletePendingIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskId: String
    ): PendingIntent {
        val intent = createCompleteIntent(context, token, taskId)
        val requestCode = getRequestCode("complete", token.accountKey, token.generation, taskId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun createOpenIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskId: String
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_TASK
            data = buildOpenDataUri(token, taskId)
            putExtra(EXTRA_ACCOUNT_KEY, token.accountKey.value)
            putExtra(EXTRA_GENERATION, token.generation)
            putExtra(EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    fun createOpenPendingIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskId: String
    ): PendingIntent {
        val intent = createOpenIntent(context, token, taskId)
        val requestCode = getRequestCode("open", token.accountKey, token.generation, taskId)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun createDismissIntent(
        context: Context,
        key: DeadlineSelectionKey,
        receipt: String
    ): Intent {
        val encodedKey = DeadlineSelectionKeyCodec.encode(key)
        return Intent(context, TaskSurfaceDismissReceiver::class.java).apply {
            action = ACTION_DISMISS_SURFACE_NOTIFICATION
            data = buildDismissDataUri(key.token, key.taskId, receipt)
            putExtra(EXTRA_ACCOUNT_KEY, key.token.accountKey.value)
            putExtra(EXTRA_GENERATION, key.token.generation)
            putExtra(EXTRA_TASK_ID, key.taskId)
            putExtra(EXTRA_RECEIPT, receipt)
            putExtra(EXTRA_SELECTION_KEY, encodedKey)
        }
    }

    fun createDismissPendingIntent(
        context: Context,
        key: DeadlineSelectionKey,
        receipt: String
    ): PendingIntent {
        val intent = createDismissIntent(context, key, receipt)
        val requestCode = getRequestCode("dismiss", key.token.accountKey, key.token.generation, key.taskId, receipt)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
