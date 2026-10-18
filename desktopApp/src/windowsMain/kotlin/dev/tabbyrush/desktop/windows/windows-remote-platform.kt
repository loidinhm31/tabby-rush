package dev.tabbyrush.desktop.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.W32APIOptions
import dev.tabbyrush.desktop.DesktopRemotePlatformProvider
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal interface Shell32AumidLibrary : Library {
    fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int

    companion object {
        val INSTANCE: Shell32AumidLibrary? by lazy {
            try {
                Native.load("shell32", Shell32AumidLibrary::class.java, W32APIOptions.UNICODE_OPTIONS)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/**
 * Invokes Windows Shell32 SetCurrentProcessExplicitAppUserModelID.
 * Must be called early on process startup before any notification or window registration.
 */
fun setCurrentProcessExplicitAppUserModelID(aumid: String = "dev.tabbyrush.desktop"): Boolean {
    return try {
        val lib = Shell32AumidLibrary.INSTANCE ?: return false
        val hr = lib.SetCurrentProcessExplicitAppUserModelID(WString(aumid))
        hr == 0
    } catch (_: Throwable) {
        false
    }
}

/**
 * Windows target platform provider instantiating the real [WindowsRemoteNotificationAdapter]
 * bridging remote announcements to tabbyrush-toast.dll and JNA WinRT toast notifier.
 */
class WindowsRemotePlatformProvider : DesktopRemotePlatformProvider {
    override val target: String = "windows"

    override fun onProcessStartup() {
        setCurrentProcessExplicitAppUserModelID("dev.tabbyrush.desktop")
    }

    override fun createAdapter(
        ioDispatcher: CoroutineDispatcher,
        uiDispatcher: CoroutineDispatcher,
        onActivationRequest: (() -> Unit)?
    ): RemoteNotificationAdapter {
        // Startup cleanup of old "tr-remote" notifications is automatically executed in WindowsRemoteNotificationAdapter init
        return WindowsRemoteNotificationAdapter(
            ioDispatcher = ioDispatcher
        )
    }
}
