package dev.tabbyrush.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.DeadlineSelectionKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.TaskRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

enum class TaskSurfaceAvailability {
    OptedOut,
    PermissionDenied,
    AppDisabled,
    ChannelDisabled,
    Available
}

data class TaskSurfacePreferenceState(
    val enabled: Boolean = false,
    val suppressed: DeadlineSelectionKey? = null,
    val postedKey: DeadlineSelectionKey? = null,
    val postedReceipt: String? = null,
    val hasCorruptSuppression: Boolean = false
)

/**
 * Robust, versioned serializer for DeadlineSelectionKey that preserves all TaskDeadline
 * fields (including zone, offsets, anchors, boundary semantics) without persisting user task titles.
 * Avoids platform JSON stubbing on JVM unit tests by using standard java.net encoding.
 */
object DeadlineSelectionKeyCodec {
    private const val PREFIX_V1 = "v1:"

    fun encode(key: DeadlineSelectionKey): String {
        val params = StringBuilder(PREFIX_V1)
        fun appendParam(name: String, value: String) {
            params.append(name)
                .append('=')
                .append(URLEncoder.encode(value, "UTF-8"))
                .append('&')
        }

        appendParam("acc", key.token.accountKey.value)
        appendParam("gen", key.token.generation.toString())
        appendParam("owner", key.taskRef.ownerUserId)
        appendParam("rowId", key.taskRef.rowId)
        appendParam("tid", key.taskId)
        appendParam("date", key.deadline.date)
        if (key.deadline.time != null) {
            appendParam("time", key.deadline.time!!)
        }
        appendParam("zone", key.deadline.zone)
        appendParam("offset", key.deadline.offsetSeconds.toString())
        if (key.deadline.reminderAnchorTime != null) {
            appendParam("anchorTime", key.deadline.reminderAnchorTime!!)
        }
        appendParam("anchorEpoch", key.deadline.anchorEpochMs.toString())
        appendParam("overdueEpoch", key.deadline.overdueEpochMs.toString())

        return params.toString()
    }

    fun decode(raw: String?): DeadlineSelectionKey? {
        if (raw.isNullOrBlank() || !raw.startsWith(PREFIX_V1)) return null
        return try {
            val content = raw.substring(PREFIX_V1.length)
            val map = content.split('&')
                .filter { it.contains('=') }
                .associate {
                    val idx = it.indexOf('=')
                    val k = it.substring(0, idx)
                    val v = URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    k to v
                }

            val acc = map["acc"] ?: return null
            val gen = map["gen"]?.toLongOrNull() ?: return null
            val owner = map["owner"] ?: return null
            val rowId = map["rowId"] ?: map["tid"] ?: return null
            if (owner.isBlank() || rowId.isBlank()) return null
            val date = map["date"] ?: return null
            val time = map["time"]
            val zone = map["zone"] ?: return null
            val offset = map["offset"]?.toLongOrNull() ?: return null
            val anchorTime = map["anchorTime"]
            val anchorEpoch = map["anchorEpoch"]?.toLongOrNull() ?: return null
            val overdueEpoch = map["overdueEpoch"]?.toLongOrNull() ?: return null

            val deadline = TaskDeadline(
                date = date,
                time = time,
                zone = zone,
                offsetSeconds = offset,
                reminderAnchorTime = anchorTime,
                anchorEpochMs = anchorEpoch,
                overdueEpochMs = overdueEpoch
            )
            DeadlineSelectionKey(
                token = SurfaceAccountToken(AccountKey(acc), gen),
                taskRef = TaskRef(ownerUserId = owner, rowId = rowId),
                deadline = deadline
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Account-scoped SharedPreferences wrapper outside Room/task sync.
 * Atomically stores opt-in state, durable suppression keys, and active posted receipts.
 * All mutations enforce synchronous commit() writes and fail closed.
 */
class TaskSurfacePreferences(private val context: Context) {

    private fun getPrefs(accountKey: AccountKey): SharedPreferences {
        return context.getSharedPreferences(getPrefName(accountKey), Context.MODE_PRIVATE)
    }

    private fun readInternal(accountKey: AccountKey): TaskSurfacePreferenceState {
        val prefs = getPrefs(accountKey)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val suppressedRaw = prefs.getString(KEY_SUPPRESSED_KEY, null)
        val postedKeyRaw = prefs.getString(KEY_POSTED_KEY, null)
        val postedReceipt = prefs.getString(KEY_POSTED_RECEIPT, null)

        val suppressed = if (suppressedRaw != null) DeadlineSelectionKeyCodec.decode(suppressedRaw) else null
        val hasCorrupt = suppressedRaw != null && suppressed == null
        val postedKey = if (postedKeyRaw != null) DeadlineSelectionKeyCodec.decode(postedKeyRaw) else null

        return TaskSurfacePreferenceState(
            enabled = enabled,
            suppressed = suppressed,
            postedKey = postedKey,
            postedReceipt = postedReceipt,
            hasCorruptSuppression = hasCorrupt
        )
    }

    suspend fun read(accountKey: AccountKey): TaskSurfacePreferenceState = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readInternal(accountKey)
        }
    }

    suspend fun setEnabled(token: SurfaceAccountToken, enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val prefs = getPrefs(token.accountKey)
            val currentEnabled = prefs.getBoolean(KEY_ENABLED, false)
            val editor = prefs.edit()
            editor.putBoolean(KEY_ENABLED, enabled)

            // When toggling or disabling, clear suppression and active post receipt
            if (currentEnabled != enabled) {
                editor.remove(KEY_SUPPRESSED_KEY)
                editor.remove(KEY_POSTED_KEY)
                editor.remove(KEY_POSTED_RECEIPT)
            }

            val committed = editor.commit()
            check(committed) { "Failed to commit setEnabled($enabled) for ${token.accountKey}" }
        }
    }

    suspend fun reconcileSelection(
        token: SurfaceAccountToken,
        key: DeadlineSelectionKey?
    ): TaskSurfacePreferenceState = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (key != null) {
                require(key.token == token) {
                    "Token mismatch in reconcileSelection: expected $token, got ${key.token}"
                }
            }

            val current = readInternal(token.accountKey)
            val editor = getPrefs(token.accountKey).edit()
            var mutated = false

            var newSuppressed = current.suppressed
            var newPostedKey = current.postedKey
            var newPostedReceipt = current.postedReceipt
            var newCorrupt = current.hasCorruptSuppression

            if (key == null) {
                // Selection disappeared => clear suppression and active post
                if (current.suppressed != null || current.hasCorruptSuppression) {
                    editor.remove(KEY_SUPPRESSED_KEY)
                    newSuppressed = null
                    newCorrupt = false
                    mutated = true
                }
                if (current.postedKey != null || current.postedReceipt != null) {
                    editor.remove(KEY_POSTED_KEY)
                    editor.remove(KEY_POSTED_RECEIPT)
                    newPostedKey = null
                    newPostedReceipt = null
                    mutated = true
                }
            } else {
                // Selection exists: if suppressed key does not match this selection, clear suppression
                if (current.suppressed != null && current.suppressed != key) {
                    editor.remove(KEY_SUPPRESSED_KEY)
                    newSuppressed = null
                    mutated = true
                }
                // If active posted key is for a different key, invalidate post receipt
                if (current.postedKey != null && current.postedKey != key) {
                    editor.remove(KEY_POSTED_KEY)
                    editor.remove(KEY_POSTED_RECEIPT)
                    newPostedKey = null
                    newPostedReceipt = null
                    mutated = true
                }
            }

            if (mutated) {
                val committed = editor.commit()
                check(committed) { "Failed to commit reconcileSelection for ${token.accountKey}" }
            }

            TaskSurfacePreferenceState(
                enabled = current.enabled,
                suppressed = newSuppressed,
                postedKey = newPostedKey,
                postedReceipt = newPostedReceipt,
                hasCorruptSuppression = newCorrupt
            )
        }
    }

    suspend fun recordPost(key: DeadlineSelectionKey, receipt: String): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val encodedKey = DeadlineSelectionKeyCodec.encode(key)
            val editor = getPrefs(key.token.accountKey).edit()
            editor.putString(KEY_POSTED_KEY, encodedKey)
            editor.putString(KEY_POSTED_RECEIPT, receipt)
            val committed = editor.commit()
            check(committed) { "Failed to commit recordPost for ${key.token.accountKey}" }
        }
    }

    suspend fun suppressIfCurrent(key: DeadlineSelectionKey, receipt: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readInternal(key.token.accountKey)
            if (current.postedKey == key && current.postedReceipt == receipt) {
                val editor = getPrefs(key.token.accountKey).edit()
                editor.putString(KEY_SUPPRESSED_KEY, DeadlineSelectionKeyCodec.encode(key))
                editor.remove(KEY_POSTED_KEY)
                editor.remove(KEY_POSTED_RECEIPT)
                val committed = editor.commit()
                check(committed) { "Failed to commit suppressIfCurrent for ${key.token.accountKey}" }
                true
            } else {
                false
            }
        }
    }

    suspend fun invalidatePost(accountKey: AccountKey): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val editor = getPrefs(accountKey).edit()
            editor.remove(KEY_POSTED_KEY)
            editor.remove(KEY_POSTED_RECEIPT)
            val committed = editor.commit()
            check(committed) { "Failed to commit invalidatePost for $accountKey" }
        }
    }

    suspend fun clear(accountKey: AccountKey): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val prefName = getPrefName(accountKey)
            val editor = getPrefs(accountKey).edit()
            editor.clear()
            val committed = editor.commit()
            check(committed) { "Failed to commit clear for $accountKey" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    context.deleteSharedPreferences(prefName)
                } catch (_: Exception) {
                    // Best effort file deletion on Android N+
                }
            }
        }
    }

    companion object {
        private val lock = Any()

        const val KEY_ENABLED = "task_surface_enabled"
        const val KEY_SUPPRESSED_KEY = "task_surface_suppressed_key"
        const val KEY_POSTED_KEY = "task_surface_posted_key"
        const val KEY_POSTED_RECEIPT = "task_surface_posted_receipt"

        fun getPrefName(accountKey: AccountKey): String = "tabbyrush_task_surface_${accountKey.value}"
    }
}
