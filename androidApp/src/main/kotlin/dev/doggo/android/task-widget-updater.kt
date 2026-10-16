package dev.doggo.android

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.SurfaceResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coalescing widget updater managing all installed DoggoTaskWidget instances.
 * Enforces atomic publication fencing, bounded readiness and OS update timeouts,
 * active job tracking for quiescence, and guaranteed non-sensitive clearing on teardown.
 */
object TaskWidgetUpdater {

    private const val TAG = "TaskWidgetUpdater"
    private const val BOUNDED_TIMEOUT_MS = 4_000L

    private val isClearing = AtomicBoolean(false)
    private val epoch = AtomicLong(0L)
    private val updateMutex = Mutex()
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Refreshes all installed DoggoTaskWidget instances against the authoritative host.
     * Bounded to 4s readiness and 4s update handoff; rejects publication if superseded by
     * a concurrent clearing or account transition.
     */
    suspend fun refreshAll(context: Context, host: AndroidTaskSurfaceHost) {
        val ticket = epoch.incrementAndGet()

        if (isClearing.get()) {
            Log.d(TAG, "Skipping refreshAll(ticket=$ticket): clearing operation is currently active")
            return
        }

        val job = coroutineContext[Job]
        if (job != null) {
            activeJobs[ticket] = job
        }

        try {
            updateMutex.withLock {
                if (isClearing.get() || ticket < epoch.get()) {
                    Log.d(TAG, "Discarding stale refresh ticket $ticket (current epoch=${epoch.get()})")
                    return@withLock
                }

                val readiness = try {
                    withTimeoutOrNull(BOUNDED_TIMEOUT_MS) {
                        host.access.awaitReadiness(BOUNDED_TIMEOUT_MS)
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    Log.w(TAG, "Readiness await threw exception", t)
                    SurfaceReadiness.Unavailable
                } ?: SurfaceReadiness.Unavailable

                when (readiness) {
                    is SurfaceReadiness.Ready -> {
                        val result = host.access.read(readiness.token)
                        when (result) {
                            is SurfaceResult.Value -> {
                                if (isClearing.get() || ticket < epoch.get()) {
                                    Log.d(TAG, "Aborting publication for ticket $ticket: cleared or superseded")
                                    return@withLock
                                }

                                val published = host.access.publishIfCurrent(readiness.token) {
                                    DoggoTaskWidget.updateDisplayState(
                                        WidgetDisplayState.Content(
                                            token = readiness.token,
                                            sections = result.value.sections
                                        )
                                    )
                                    publishToGlance(context)
                                }

                                if (!published) {
                                    Log.d(TAG, "publishIfCurrent returned false for token ${readiness.token}")
                                }
                            }
                            SurfaceResult.Rejected -> {
                                Log.d(TAG, "Snapshot read rejected for token ${readiness.token}; resetting to SignedOut")
                                DoggoTaskWidget.updateDisplayState(WidgetDisplayState.SignedOut)
                                publishToGlance(context)
                            }
                            SurfaceResult.Unavailable -> {
                                Log.d(TAG, "Snapshot read unavailable for token ${readiness.token}")
                                DoggoTaskWidget.updateDisplayState(WidgetDisplayState.Unavailable)
                                publishToGlance(context)
                            }
                        }
                    }
                    SurfaceReadiness.SignedOut -> {
                        Log.d(TAG, "Host readiness SignedOut; displaying signed out state")
                        DoggoTaskWidget.updateDisplayState(WidgetDisplayState.SignedOut)
                        publishToGlance(context)
                    }
                    SurfaceReadiness.Locked -> {
                        Log.d(TAG, "Host readiness Locked; displaying locked state")
                        DoggoTaskWidget.updateDisplayState(WidgetDisplayState.Locked)
                        publishToGlance(context)
                    }
                    SurfaceReadiness.Unavailable,
                    SurfaceReadiness.Restoring -> {
                        Log.d(TAG, "Host readiness Unavailable or Restoring; displaying placeholder")
                        DoggoTaskWidget.updateDisplayState(WidgetDisplayState.Unavailable)
                        publishToGlance(context)
                    }
                }
            }
        } finally {
            activeJobs.remove(ticket)
        }
    }

    /**
     * Clears all installed widget instances to signed-out/empty state and neutralizes
     * active or pending update publications.
     */
    suspend fun clearAll(context: Context) {
        clearAllInstances(context)
    }

    /**
     * Complete lifecycle clearing and session drain for all installed launcher IDs.
     * Prevents old session updates from restoring prior account titles.
     */
    suspend fun clearAllInstances(context: Context) {
        isClearing.set(true)
        val clearTicket = epoch.incrementAndGet()
        Log.i(TAG, "clearAllInstances initiating with ticket $clearTicket")

        try {
            // Cancel all tracked in-flight update jobs
            for ((ticket, job) in activeJobs) {
                job.cancel()
                activeJobs.remove(ticket)
            }

            updateMutex.withLock {
                // Set memory state to SignedOut so any active composition renders non-sensitive UI
                DoggoTaskWidget.clearState()

                // 1. Direct RemoteViews push for immediate launcher blanking without waiting for compose cycles
                pushBlankRemoteViews(context)

                // 2. Update Glance instances to SignedOut
                publishToGlance(context)

                // 3. Close running Glance sessions
                closeGlanceSessions(context)

                // 4. Await session and update quiescence
                awaitQuiescence(BOUNDED_TIMEOUT_MS)

                Log.i(TAG, "clearAllInstances completed successfully (quiescence established)")
            }
        } finally {
            isClearing.set(false)
        }
    }

    /**
     * Awaits completion or cancellation of all tracked background update jobs.
     */
    suspend fun awaitQuiescence(timeoutMs: Long = BOUNDED_TIMEOUT_MS) {
        withTimeoutOrNull(timeoutMs) {
            val jobs = activeJobs.values.toList()
            jobs.joinAll()
        }
    }

    private suspend fun publishToGlance(context: Context) {
        try {
            withTimeoutOrNull(BOUNDED_TIMEOUT_MS) {
                DoggoTaskWidget().updateAll(context)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "publishToGlance updateAll encountered an error", t)
        }
    }

    private fun pushBlankRemoteViews(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, DoggoTaskWidgetReceiver::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val layoutId = context.resources.getIdentifier("task_widget_initial", "layout", context.packageName)
            if (layoutId != 0) {
                val views = RemoteViews(context.packageName, layoutId)
                val titleId = context.resources.getIdentifier("widget_initial_title", "id", context.packageName)
                val subtitleId = context.resources.getIdentifier("widget_initial_subtitle", "id", context.packageName)
                if (titleId != 0) {
                    views.setTextViewText(titleId, context.getString(R.string.task_widget_title))
                }
                if (subtitleId != 0) {
                    views.setTextViewText(subtitleId, context.getString(R.string.task_widget_signed_out))
                }
                appWidgetManager.updateAppWidget(ids, views)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "pushBlankRemoteViews encountered an error", t)
        }
    }

    private suspend fun closeGlanceSessions(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(DoggoTaskWidget::class.java)
            val sessionManager = DoggoTaskWidget().getSessionManager(context)
            sessionManager.runWithLock {
                for (glanceId in glanceIds) {
                    val appWidgetId = manager.getAppWidgetId(glanceId)
                    closeSession("appWidget-$appWidgetId")
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.d(TAG, "closeGlanceSessions encountered non-fatal error: ${t.message}")
        }
    }
}
