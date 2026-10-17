package dev.tabbyrush.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Private receiver monitoring system time, time-zone, and date changes.
 * Enforces a bounded debounce window to coalesce rapid consecutive OS clock events
 * before requesting a surface clock refresh.
 */
class TaskSurfaceTimeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskSurfaceTimeRcvr"
        const val DEBOUNCE_WINDOW_MS = 500L
        private val lastDispatchElapsedMs = AtomicLong(0L)

        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) {
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val prev = lastDispatchElapsedMs.get()
        if (nowElapsed - prev < DEBOUNCE_WINDOW_MS) {
            Log.d(TAG, "Debouncing rapid clock broadcast $action (${nowElapsed - prev}ms since last)")
            return
        }

        if (!lastDispatchElapsedMs.compareAndSet(prev, nowElapsed)) {
            // Another thread claimed the debounce slot
            return
        }

        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner ?: return
        val host = hostOwner.taskSurfaces
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                host.refresh(SurfaceRefreshReason.Clock)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing surface after clock broadcast $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
