package dev.doggo.desktop.windows

import com.sun.jna.Platform

/**
 * Result status for Windows toast notification actions.
 */
sealed interface WindowsNotificationStatus {
    data class Success(val tag: String, val group: String) : WindowsNotificationStatus
    data class Cleared(val tag: String, val group: String) : WindowsNotificationStatus
    data class Unavailable(val reason: String) : WindowsNotificationStatus
    data class Error(val message: String) : WindowsNotificationStatus
}

/**
 * Interface abstracting Windows toast notification delivery and history removal.
 */
interface WindowsToastBridge {
    fun isAvailable(): Boolean
    fun showToast(aumid: String, xmlPayload: String, tag: String, group: String): WindowsNotificationStatus
    fun clearToast(aumid: String, tag: String, group: String): WindowsNotificationStatus
    fun clearAllToasts(aumid: String): WindowsNotificationStatus
}

/**
 * Candidate Windows toast bridge using documented AppUserModelId (AUMID) route.
 * Note: Status is Candidate / Unproven pending live execution on an installed Windows host with MSI AUMID registration.
 */
class JnaWindowsToastBridge : WindowsToastBridge {
    override fun isAvailable(): Boolean {
        // Pending Phase 09 live COM/WinRT implementation on installed Windows host
        return false
    }

    override fun showToast(
        aumid: String,
        xmlPayload: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        return WindowsNotificationStatus.Unavailable(
            "Native COM/WinRT toast bridge unproven; pending Phase 09 Windows testbed"
        )
    }

    override fun clearToast(
        aumid: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        return WindowsNotificationStatus.Unavailable(
            "Native COM/WinRT toast bridge unproven; pending Phase 09 Windows testbed"
        )
    }

    override fun clearAllToasts(aumid: String): WindowsNotificationStatus {
        return WindowsNotificationStatus.Unavailable(
            "Native COM/WinRT toast bridge unproven; pending Phase 09 Windows testbed"
        )
    }
}

/**
 * Candidate Windows notification adapter managing AUMID toast presentation and history removal.
 */
class WindowsNotificationAdapter(
    private val bridge: WindowsToastBridge = JnaWindowsToastBridge()
) {
    companion object {
        const val AUMID = "dev.doggo.desktop"
        const val DEFAULT_GROUP = "doggo_reminders"

        fun escapeXml(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun buildToastXml(title: String, message: String): String {
            val safeTitle = escapeXml(title)
            val safeMessage = escapeXml(message)
            return """<toast><visual><binding template="ToastGeneric"><text>$safeTitle</text><text>$safeMessage</text></binding></visual></toast>"""
        }
    }

    fun isAvailable(): Boolean = bridge.isAvailable()

    fun postNotification(
        tag: String,
        title: String,
        message: String,
        group: String = DEFAULT_GROUP
    ): WindowsNotificationStatus {
        val xml = buildToastXml(title, message)
        return bridge.showToast(AUMID, xml, tag, group)
    }

    fun cancelNotification(
        tag: String,
        group: String = DEFAULT_GROUP
    ): WindowsNotificationStatus {
        return bridge.clearToast(AUMID, tag, group)
    }

    fun cancelAllNotifications(): WindowsNotificationStatus {
        return bridge.clearAllToasts(AUMID)
    }
}
