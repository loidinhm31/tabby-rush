package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Platform provider contract for desktop remote notifications.
 * Implementations are target-specific (Linux vs Windows) and discovered via [DesktopRemotePlatformDispatcher].
 */
interface DesktopRemotePlatformProvider {
    val target: String

    /**
     * Target-specific early process startup configuration (e.g. Windows explicit AUMID).
     */
    fun onProcessStartup() {}

    /**
     * Creates a target-specific [RemoteNotificationAdapter].
     */
    fun createAdapter(
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        uiDispatcher: CoroutineDispatcher = Dispatchers.Default,
        onActivationRequest: (() -> Unit)? = null
    ): RemoteNotificationAdapter
}

/**
 * Dispatcher discovering the active platform-specific [DesktopRemotePlatformProvider].
 *
 * Invariants:
 * 1. Platform-neutral boundary isolating target-specific dependencies (D-Bus on Linux, WinRT/JNA on Windows).
 * 2. If target provider is missing or cannot be loaded, fails closed with [UnavailableRemoteNotificationAdapter]
 *    exposing capability limitation with notifications disabled, never fake delivery success.
 * 3. Supports test overrides via [setForTest].
 */
object DesktopRemotePlatformDispatcher {

    @Volatile
    private var testOverride: DesktopRemotePlatformProvider? = null

    fun detectHostTarget(): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> "unsupported"
        }
    }

    fun setForTest(provider: DesktopRemotePlatformProvider?) {
        testOverride = provider
    }

    fun resolveProvider(
        target: String = detectHostTarget()
    ): DesktopRemotePlatformProvider {
        testOverride?.let { return it }

        val className = when (target) {
            "linux" -> "dev.tabbyrush.desktop.linux.LinuxRemotePlatformProvider"
            "windows" -> "dev.tabbyrush.desktop.windows.WindowsRemotePlatformProvider"
            else -> null
        }

        if (className != null) {
            try {
                val clazz = Class.forName(className)
                val instance = clazz.getDeclaredConstructor().newInstance()
                if (instance is DesktopRemotePlatformProvider) {
                    return instance
                }
            } catch (t: Throwable) {
                return UnavailableRemotePlatformProvider(target, t.message ?: t.toString())
            }
        }

        return UnavailableRemotePlatformProvider(target, "Unsupported desktop target: '$target'")
    }

    fun onProcessStartup(target: String = detectHostTarget()) {
        resolveProvider(target).onProcessStartup()
    }

    fun createAdapter(
        target: String = detectHostTarget(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        uiDispatcher: CoroutineDispatcher = Dispatchers.Default,
        onActivationRequest: (() -> Unit)? = null
    ): RemoteNotificationAdapter {
        return resolveProvider(target).createAdapter(ioDispatcher, uiDispatcher, onActivationRequest)
    }

    fun setCurrentProcessExplicitAppUserModelID(aumid: String = "dev.tabbyrush.desktop"): Boolean {
        if (detectHostTarget() == "windows") {
            return try {
                val clazz = Class.forName("dev.tabbyrush.desktop.windows.WindowsRemotePlatformKt")
                val method = clazz.getMethod("setCurrentProcessExplicitAppUserModelID", String::class.java)
                (method.invoke(null, aumid) as? Boolean) ?: false
            } catch (_: Throwable) {
                false
            }
        }
        return false
    }
}

/**
 * Top-level helper to configure Windows explicit AppUserModelID on process startup.
 * No-op on non-Windows platforms.
 */
fun setCurrentProcessExplicitAppUserModelID(aumid: String = "dev.tabbyrush.desktop"): Boolean {
    return DesktopRemotePlatformDispatcher.setCurrentProcessExplicitAppUserModelID(aumid)
}

/**
 * Unavailable fallback adapter indicating remote OS alerts are not supported on this host/configuration.
 */
class UnavailableRemoteNotificationAdapter(
    val reason: String = "Platform notifications not supported"
) : RemoteNotificationAdapter {

    override suspend fun capabilities(): RemoteNotificationCapabilities = RemoteNotificationCapabilities(
        alertsSupported = false,
        actionsSupported = false,
        notificationsEnabled = false
    )

    override suspend fun show(messageId: String, generation: Long): RemotePostResult =
        RemotePostResult.Suppressed

    override suspend fun clearRemote(generation: Long) {}

    override suspend fun close() {}

    override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {}
}

class UnavailableRemotePlatformProvider(
    override val target: String,
    val reason: String
) : DesktopRemotePlatformProvider {

    override fun createAdapter(
        ioDispatcher: CoroutineDispatcher,
        uiDispatcher: CoroutineDispatcher,
        onActivationRequest: (() -> Unit)?
    ): RemoteNotificationAdapter = UnavailableRemoteNotificationAdapter(reason)
}
