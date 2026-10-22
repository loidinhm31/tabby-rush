package dev.tabbyrush.android

import android.content.Context
import android.os.UserManager
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.SurfaceSnapshot
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.TaskSurfaceAccess
import dev.tabbyrush.shared.contract.partitionSurfaceTodos
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android implementation of TaskSurfaceAccess backed by TabbyRushApplication and the active AccountRuntime.
 *
 * Invariants:
 * - Readiness states: Restoring -> Ready(token), SignedOut, Locked, or Unavailable.
 * - awaitReadiness enforces a bounded cold timeout (default 4000ms), returning Unavailable on timeout.
 * - Token verification: every read, completion, publication, and navigation operation verifies that
 *   the provided token matches the active account key and generation.
 * - Never opens an AccountRuntime for an arbitrary action-specified account.
 * - Leased read/write operations execute under ReminderDeliveryGate admission to coordinate with account teardown.
 * - OS publication handoff is serialized under publicationMutex, performing final validation before calling OS.
 * - CancellationException is never swallowed.
 */
class AndroidTaskSurfaceAccess(
    private val app: TabbyRushApplication,
    private val publicationMutex: Mutex
) : TaskSurfaceAccess {

    private val _readiness = MutableStateFlow<SurfaceReadiness>(SurfaceReadiness.Restoring)
    override val readiness: StateFlow<SurfaceReadiness> = _readiness.asStateFlow()

    fun updateReadiness(newReadiness: SurfaceReadiness) {
        _readiness.value = newReadiness
    }

    override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness {
        val current = _readiness.value
        if (current !is SurfaceReadiness.Restoring) {
            return current
        }
        return try {
            withTimeoutOrNull(timeoutMs) {
                _readiness.first { it !is SurfaceReadiness.Restoring }
            } ?: SurfaceReadiness.Unavailable
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            SurfaceReadiness.Unavailable
        }
    }

    override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> {
        val currentReadiness = _readiness.value
        if (currentReadiness !is SurfaceReadiness.Ready || currentReadiness.token != token) {
            return SurfaceResult.Rejected
        }

        val runtime = app.getActiveRuntime()
        if (runtime == null || runtime.accountKey != token.accountKey || runtime.generation != token.generation) {
            return SurfaceResult.Rejected
        }

        val userManager = app.getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            return SurfaceResult.Unavailable
        }

        if (app.accountFiles.hasPendingWipe(token.accountKey)) {
            return SurfaceResult.Rejected
        }

        return try {
            runtime.deliveryGate.withDeliveryAdmission(token.generation) {
                try {
                    val todos = runtime.store.loadTodos()
                    val schedules = runtime.store.getPersistedScheduleProjection()
                    val sections = partitionSurfaceTodos(todos)
                    SurfaceResult.Value(
                        SurfaceSnapshot(
                            token = token,
                            sections = sections,
                            schedules = schedules
                        )
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    SurfaceResult.Unavailable
                }
            } ?: SurfaceResult.Rejected
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            SurfaceResult.Unavailable
        }
    }

    override suspend fun setCompleted(
        token: SurfaceAccountToken,
        taskRef: TaskRef,
        completed: Boolean,
        nowEpochMs: Long
    ): SurfaceResult<CompletionChange> {
        val currentReadiness = _readiness.value
        if (currentReadiness !is SurfaceReadiness.Ready || currentReadiness.token != token) {
            return SurfaceResult.Rejected
        }

        val runtime = app.getActiveRuntime()
        if (runtime == null || runtime.accountKey != token.accountKey || runtime.generation != token.generation) {
            return SurfaceResult.Rejected
        }

        val userManager = app.getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            return SurfaceResult.Unavailable
        }

        if (app.accountFiles.hasPendingWipe(token.accountKey)) {
            return SurfaceResult.Rejected
        }

        var changeResult: CompletionChange? = null
        val admitted = try {
            runtime.deliveryGate.withDeliveryAdmission(token.generation) {
                try {
                    val access = runtime.store.accessFor(taskRef)
                    val isOwner = taskRef.ownerUserId == runtime.store.userId
                    val canEdit = if (isOwner) true else (access?.canEditContent == true)
                    if (!canEdit) {
                        return@withDeliveryAdmission SurfaceResult.Rejected
                    }
                    val change = runtime.store.setCompletedLocally(taskRef, completed, nowEpochMs)
                    changeResult = change
                    SurfaceResult.Value(change)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    SurfaceResult.Unavailable
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            SurfaceResult.Unavailable
        }

        val finalResult = admitted ?: SurfaceResult.Rejected

        // Request background sync only after local lease commit outside gate admission
        if (changeResult == CompletionChange.Changed) {
            try {
                app.syncScheduler.triggerImmediate(token.accountKey)
            } catch (_: Exception) {
                // Sync failure does not invalidate local commit
            }
        }

        return finalResult
    }

    override suspend fun publishIfCurrent(
        token: SurfaceAccountToken,
        publish: suspend () -> Unit
    ): Boolean {
        return publicationMutex.withLock {
            val currentReadiness = _readiness.value
            if (currentReadiness !is SurfaceReadiness.Ready || currentReadiness.token != token) {
                return@withLock false
            }

            val runtime = app.getActiveRuntime()
            if (runtime == null || runtime.accountKey != token.accountKey || runtime.generation != token.generation) {
                return@withLock false
            }

            val userManager = app.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null && !userManager.isUserUnlocked) {
                return@withLock false
            }

            if (app.accountFiles.hasPendingWipe(token.accountKey)) {
                return@withLock false
            }
            val initialAccessGen = runtime.store.accessGeneration
            try {
                if (runtime.store.accessGeneration != initialAccessGen) {
                    return@withLock false
                }
                publish()
                true
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun validateNavigation(
        token: SurfaceAccountToken,
        taskRef: TaskRef?
    ): Boolean {
        val currentReadiness = _readiness.value
        if (currentReadiness !is SurfaceReadiness.Ready || currentReadiness.token != token) {
            return false
        }

        val runtime = app.getActiveRuntime()
        if (runtime == null || runtime.accountKey != token.accountKey || runtime.generation != token.generation) {
            return false
        }

        val userManager = app.getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            return false
        }

        if (app.accountFiles.hasPendingWipe(token.accountKey)) {
            return false
        }

        if (taskRef == null) {
            return true
        }

        return try {
            val todo = runtime.store.getTodo(taskRef)
            todo != null && !todo.isDeleted
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

}
