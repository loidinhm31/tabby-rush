package dev.tabbyrush.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.DeadlineSelectionKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.TaskRef
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
    const val EXTRA_OWNER_USER_ID = "extra_owner_user_id"
    const val EXTRA_ROW_ID = "extra_row_id"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_RECEIPT = "extra_receipt"
    const val EXTRA_SELECTION_KEY = "extra_selection_key"
    data class ParsedTaskSurfaceUri(
        val operation: String,
        val accountKey: AccountKey,
        val generation: Long,
        val taskRef: TaskRef,
        val receipt: String? = null
    )

    fun parseDataUri(uriString: String?): ParsedTaskSurfaceUri? {
        if (uriString.isNullOrBlank()) return null
        if (!uriString.startsWith("tabbyrush://task-surface/")) return null
        val path = uriString.removePrefix("tabbyrush://task-surface/").split('/')
        if (path.size < 5) return null
        val op = path[0]
        val accountKey = AccountKey(path[1])
        val gen = path[2].toLongOrNull() ?: return null
        val owner = path[3]
        val rowId = path[4]
        if (owner.isBlank() || rowId.isBlank()) return null
        val receipt = if (path.size >= 6) path[5] else null
        return ParsedTaskSurfaceUri(op, accountKey, gen, TaskRef(owner, rowId), receipt)
    }

    fun parseDataUri(uri: Uri?): ParsedTaskSurfaceUri? {
        if (uri == null) return null
        val scheme = try { uri.scheme } catch (_: Exception) { null }
        val authority = try { uri.authority } catch (_: Exception) { null }
        if (scheme != "tabbyrush" || authority != "task-surface") return null
        val segments = try { uri.pathSegments } catch (_: Exception) { null } ?: return null
        if (segments.size < 5) return null
        val op = segments[0]
        val accountKey = AccountKey(segments[1])
        val gen = segments[2].toLongOrNull() ?: return null
        val owner = segments[3]
        val rowId = segments[4]
        if (owner.isBlank() || rowId.isBlank()) return null
        val receipt = if (segments.size >= 6) segments[5] else null
        return ParsedTaskSurfaceUri(op, accountKey, gen, TaskRef(owner, rowId), receipt)
    }

    fun getRequestCode(
        operation: String,
        accountKey: AccountKey,
        generation: Long,
        taskRef: TaskRef,
        suffix: String = ""
    ): Int {
        var h = operation.hashCode()
        h = 31 * h + accountKey.value.hashCode()
        h = 31 * h + generation.hashCode()
        h = 31 * h + taskRef.ownerUserId.hashCode()
        h = 31 * h + taskRef.rowId.hashCode()
        if (suffix.isNotEmpty()) {
            h = 31 * h + suffix.hashCode()
        }
        return h and 0x7FFFFFFF
    }

    fun buildCompleteDataUri(token: SurfaceAccountToken, taskRef: TaskRef): Uri {
        return Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-surface")
            .appendPath("complete")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskRef.ownerUserId)
            .appendPath(taskRef.rowId)
            .build()
    }

    fun buildOpenDataUri(token: SurfaceAccountToken, taskRef: TaskRef): Uri {
        return Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-surface")
            .appendPath("open")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskRef.ownerUserId)
            .appendPath(taskRef.rowId)
            .build()
    }

    fun buildDismissDataUri(token: SurfaceAccountToken, taskRef: TaskRef, receipt: String): Uri {
        return Uri.Builder()
            .scheme("tabbyrush")
            .authority("task-surface")
            .appendPath("dismiss")
            .appendPath(token.accountKey.value)
            .appendPath(token.generation.toString())
            .appendPath(taskRef.ownerUserId)
            .appendPath(taskRef.rowId)
            .appendPath(receipt)
            .build()
    }

    fun createCompleteIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskRef: TaskRef
    ): Intent {
        return Intent(context, TaskSurfaceActionReceiver::class.java).apply {
            action = ACTION_COMPLETE_SURFACE_TASK
            data = buildCompleteDataUri(token, taskRef)
            putExtra(EXTRA_ACCOUNT_KEY, token.accountKey.value)
            putExtra(EXTRA_GENERATION, token.generation)
            putExtra(EXTRA_OWNER_USER_ID, taskRef.ownerUserId)
            putExtra(EXTRA_ROW_ID, taskRef.rowId)
            putExtra(EXTRA_TASK_ID, taskRef.rowId)
        }
    }

    fun createCompletePendingIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskRef: TaskRef
    ): PendingIntent {
        val intent = createCompleteIntent(context, token, taskRef)
        val requestCode = getRequestCode("complete", token.accountKey, token.generation, taskRef)
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
        taskRef: TaskRef
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_TASK
            data = buildOpenDataUri(token, taskRef)
            putExtra(EXTRA_ACCOUNT_KEY, token.accountKey.value)
            putExtra(EXTRA_GENERATION, token.generation)
            putExtra(EXTRA_OWNER_USER_ID, taskRef.ownerUserId)
            putExtra(EXTRA_ROW_ID, taskRef.rowId)
            putExtra(EXTRA_TASK_ID, taskRef.rowId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    fun createOpenPendingIntent(
        context: Context,
        token: SurfaceAccountToken,
        taskRef: TaskRef
    ): PendingIntent {
        val intent = createOpenIntent(context, token, taskRef)
        val requestCode = getRequestCode("open", token.accountKey, token.generation, taskRef)
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
            data = buildDismissDataUri(key.token, key.taskRef, receipt)
            putExtra(EXTRA_ACCOUNT_KEY, key.token.accountKey.value)
            putExtra(EXTRA_GENERATION, key.token.generation)
            putExtra(EXTRA_OWNER_USER_ID, key.taskRef.ownerUserId)
            putExtra(EXTRA_ROW_ID, key.taskRef.rowId)
            putExtra(EXTRA_TASK_ID, key.taskRef.rowId)
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
        val requestCode = getRequestCode("dismiss", key.token.accountKey, key.token.generation, key.taskRef, receipt)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
