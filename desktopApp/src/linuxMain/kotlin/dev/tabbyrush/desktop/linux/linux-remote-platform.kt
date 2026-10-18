package dev.tabbyrush.desktop.linux

import dev.tabbyrush.desktop.DesktopRemotePlatformProvider
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Linux target platform provider instantiating the real [LinuxRemoteNotificationAdapter]
 * bridging remote announcements to the persistent Freedesktop D-Bus session.
 */
class LinuxRemotePlatformProvider : DesktopRemotePlatformProvider {
    override val target: String = "linux"

    override fun createAdapter(
        ioDispatcher: CoroutineDispatcher,
        uiDispatcher: CoroutineDispatcher,
        onActivationRequest: (() -> Unit)?
    ): RemoteNotificationAdapter {
        return LinuxRemoteNotificationAdapter(
            ioDispatcher = ioDispatcher,
            uiDispatcher = uiDispatcher,
            onActivationRequest = onActivationRequest
        )
    }
}
