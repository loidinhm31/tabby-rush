package dev.tabbyrush.android

import android.util.Log
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TaskSurfaceRuntimeCoordinator implements AndroidTaskSurfaceHost, providing:
 * - Publication serialization via publicationMutex.
 * - Coalesced refresh dispatch across data, startup, account, clock, capability, and widget events.
 * - Non-Ready transition under publicationMutex before resource draining.
 */
class TaskSurfaceRuntimeCoordinator(
    private val app: TabbyRushApplication
) : AndroidTaskSurfaceHost {

    private val publicationMutex = Mutex()
    private val refreshMutex = Mutex()

    override val access: AndroidTaskSurfaceAccess = AndroidTaskSurfaceAccess(app, publicationMutex)

    suspend fun transitionToNonReady(newReadiness: SurfaceReadiness) {
        publicationMutex.withLock {
            access.updateReadiness(newReadiness)
        }
    }

    suspend fun setReady(token: SurfaceAccountToken) {
        publicationMutex.withLock {
            access.updateReadiness(SurfaceReadiness.Ready(token))
        }
    }

    suspend fun setSignedOut() {
        transitionToNonReady(SurfaceReadiness.SignedOut)
    }

    suspend fun setLocked() {
        transitionToNonReady(SurfaceReadiness.Locked)
    }

    suspend fun setUnavailable() {
        transitionToNonReady(SurfaceReadiness.Unavailable)
    }

    override suspend fun refresh(reason: SurfaceRefreshReason) {
        try {
            refreshMutex.withLock {
                val currentReadiness = access.readiness.value
                if (currentReadiness is SurfaceReadiness.Ready) {
                    val token = currentReadiness.token
                    val snapshotResult = access.read(token)
                    if (snapshotResult is SurfaceResult.Value) {
                        try {
                            val preferences = TaskSurfacePreferences(app)
                            val notificationCoordinator = TaskSurfaceNotificationCoordinator(
                                context = app,
                                host = this@TaskSurfaceRuntimeCoordinator,
                                preferences = preferences
                            )
                            val now = System.currentTimeMillis()
                            notificationCoordinator.refresh(token, snapshotResult.value, now)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Log.w(TAG, "Notification refresh failed for reason $reason: ${t.message}")
                        }
                    }
                }

                try {
                    TaskWidgetUpdater.refreshAll(app, this@TaskSurfaceRuntimeCoordinator)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Widget refresh failed for reason $reason: ${t.message}")
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error in refresh for reason $reason: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "TaskSurfaceCoordinator"
    }
}
