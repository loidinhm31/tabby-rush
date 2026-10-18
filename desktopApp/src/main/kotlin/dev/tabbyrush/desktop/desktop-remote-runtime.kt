package dev.tabbyrush.desktop

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxController
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import dev.tabbyrush.shared.contract.RemoteQuiesceResult
import dev.tabbyrush.shared.remote.DefaultRemoteInboxController
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.Frame
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Interface abstracting UI restoration and unminimizing on the UI/EDT thread.
 * Enables deterministic testing in headless/test environments.
 */
fun interface DesktopUiRestorer {
    fun restoreWindow()
}

/**
 * Default Swing/AWT UI restorer unminimizing iconified frames and bringing main window to front.
 */
class DefaultSwingDesktopUiRestorer(
    private val onRestore: (() -> Unit)? = null
) : DesktopUiRestorer {
    override fun restoreWindow() {
        val action = Runnable {
            try {
                Frame.getFrames()?.forEach { frame ->
                    frame.isVisible = true
                    val state = frame.extendedState
                    if ((state and Frame.ICONIFIED) != 0) {
                        frame.extendedState = state and Frame.ICONIFIED.inv()
                    }
                    frame.toFront()
                }
            } catch (_: Throwable) {
                // Non-fatal if headless or during display disconnection
            }
            onRestore?.invoke()
        }

        if (EventQueue.isDispatchThread()) {
            action.run()
        } else {
            try {
                EventQueue.invokeLater(action)
            } catch (_: Throwable) {
                action.run()
            }
        }
    }
}

/**
 * Desktop process and session runtime for server admin notifications.
 *
 * Requirements:
 * - Manages [RemoteInboxController] lifetime across authenticated sessions and process lifecycle.
 * - Injects [RemoteNotificationAdapter] and UI dispatcher.
 * - Handles foreground refresh triggers when returning from tray or minimizing.
 * - Handles native notification activation: double-fences account generation, restores/unminimizes
 *   window via [DesktopUiRestorer] on EDT, and routes to generation-aware open path.
 * - Coordinates quiescence on account leave (implements [LeaveSchedulerCancelGate] with fail-closed timeout).
 * - Full quit lifecycle: advances generation fence, cancels streams/timers, clears remote group/IDs,
 *   and destroys adapter before exit.
 */
class DesktopRemoteRuntime(
    val adapter: RemoteNotificationAdapter,
    val scope: CoroutineScope,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val uiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val uiRestorer: DesktopUiRestorer = DefaultSwingDesktopUiRestorer(),
    val controllerFactory: (RemoteNotificationAdapter) -> RemoteInboxController
) : LeaveSchedulerCancelGate {

    constructor(
        config: HubConfig,
        authSession: HubAuthSession,
        adapter: RemoteNotificationAdapter,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        uiDispatcher: CoroutineDispatcher = Dispatchers.Default,
        onRestoreWindow: (() -> Unit)? = null
    ) : this(
        adapter = adapter,
        scope = scope,
        ioDispatcher = ioDispatcher,
        uiDispatcher = uiDispatcher,
        uiRestorer = DefaultSwingDesktopUiRestorer(onRestoreWindow),
        controllerFactory = { wrappedAdapter ->
            DefaultRemoteInboxController(
                httpClient = HubHttpClient(config),
                authSession = authSession,
                coroutineScope = scope,
                adapter = wrappedAdapter,
                isDesktop = true
            )
        }
    )

    private val activeGeneration = AtomicLong(0L)
    private val isQuiesced = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    // Intercepting adapter ensuring native activations undergo generation double-fencing
    // and UI restoration before reaching the controller
    private val activatingAdapter = object : RemoteNotificationAdapter {
        override suspend fun capabilities(): RemoteNotificationCapabilities = adapter.capabilities()
        override suspend fun show(messageId: String, generation: Long): RemotePostResult =
            adapter.show(messageId, generation)
        override suspend fun clearRemote(generation: Long) = adapter.clearRemote(generation)
        override suspend fun close() = adapter.close()

        override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {
            adapter.setActivationCallback { messageId, callbackGen ->
                handleNativeActivation(messageId, callbackGen, callback)
            }
        }
    }

    val controller: RemoteInboxController = controllerFactory(activatingAdapter)

    val state: StateFlow<RemoteInboxState> = controller.state

    fun currentGeneration(): Long = activeGeneration.get()
    fun isQuiesced(): Boolean = isQuiesced.get()
    fun isClosed(): Boolean = isClosed.get()

    /**
     * Starts an authenticated session runtime with generation fence.
     */
    suspend fun start(account: AccountIdentity, generation: Long) {
        if (isClosed.get()) return
        activeGeneration.set(generation)
        isQuiesced.set(false)
        adapter.setActivationCallback { messageId, callbackGen ->
            handleNativeActivation(messageId, callbackGen)
        }
        controller.start(account, generation)
    }

    /**
     * Dispatches user UI action to controller if session is active.
     */
    fun dispatch(action: RemoteInboxAction) {
        if (isQuiesced.get() || isClosed.get()) return
        controller.dispatch(action)
    }

    /**
     * Triggers silent refresh on foreground window reveal (e.g. restore from tray).
     */
    fun onForeground() {
        if (isQuiesced.get() || isClosed.get()) return
        controller.onForeground()
    }

    /**
     * Double-fenced native notification activation handler.
     * Fences both immediately and on the EDT queue before unminimizing window and loading announcement.
     */
    internal fun handleNativeActivation(
        messageId: String,
        callbackGen: Long,
        onValidCallback: ((String, Long) -> Unit)? = null
    ) {
        // Immediate fence: reject stale or post-quiesce callbacks
        if (isQuiesced.get() || isClosed.get() || callbackGen != activeGeneration.get()) {
            return
        }

        scope.launch(uiDispatcher) {
            // Second fence inside UI dispatcher: verify session did not switch/quiesce while task was queued
            if (isQuiesced.get() || isClosed.get() || callbackGen != activeGeneration.get()) {
                return@launch
            }

            // Restore / unminimize window
            uiRestorer.restoreWindow()

            // Route to generation-aware open path
            if (onValidCallback != null) {
                onValidCallback(messageId, callbackGen)
            } else {
                controller.onNativeOpen(messageId, callbackGen)
            }
        }
    }

    /**
     * Quiesces session for account leave or logout:
     * Advances generation fence, cancels background stream/timers, clears remote notifications on adapter,
     * and drains active jobs.
     */
    suspend fun quiesce(): RemoteQuiesceResult {
        activeGeneration.incrementAndGet()
        isQuiesced.set(true)
        return controller.quiesce()
    }

    /**
     * Implements [LeaveSchedulerCancelGate] to coordinate fail-closed drain during account leave.
     * Does not block on remote network.
     */
    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        val result = quiesce()
        if (result is RemoteQuiesceResult.TimedOut) {
            throw IllegalStateException(
                "Timed out draining remote inbox controller (${result.remainingJobs} remaining jobs) for $accountKey"
            )
        }
    }

    /**
     * Full application quit:
     * Advances generation fence, cancels background streams/timers, clears remote notifications,
     * and destroys native adapter/handlers before process exit.
     */
    suspend fun close() {
        val quitGen = activeGeneration.incrementAndGet()
        isQuiesced.set(true)
        if (isClosed.compareAndSet(false, true)) {
            try {
                controller.close()
            } catch (_: Throwable) {}
            try {
                adapter.clearRemote(quitGen)
            } catch (_: Throwable) {}
            try {
                adapter.close()
            } catch (_: Throwable) {}
        }
    }
}
