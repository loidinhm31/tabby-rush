package dev.tabbyrush.android

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android system broadcast receiver for TabbyRushTaskWidget lifecycle events.
 * Resolves TaskSurfaceHostOwner from application context, protects execution lifetime
 * with goAsync, awaits readiness safely, and delegates all-instance publication
 * and teardown to TaskWidgetUpdater.
 */
class TabbyRushTaskWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TabbyRushTaskWidget()

    private val receiverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate received for ${appWidgetIds.size} widget instances")

        // Protect asynchronous receiver lifetime across process suspension
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
                if (hostOwner != null) {
                    TaskWidgetUpdater.refreshAll(context, hostOwner.taskSurfaces)
                } else {
                    Log.d(TAG, "Application host unready during onUpdate; ensuring non-sensitive placeholder")
                    TabbyRushTaskWidget.updateDisplayState(WidgetDisplayState.SignedOut)
                    try {
                        TabbyRushTaskWidget().updateAll(context)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to update widgets with placeholder", t)
                    }
                }
            } catch (c: CancellationException) {
                Log.d(TAG, "onUpdate coroutine cancelled")
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Unhandled error in onUpdate refresh", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "onDeleted received for ${appWidgetIds.size} widget instances")
    }

    companion object {
        private const val TAG = "TaskWidgetReceiver"
    }
}
