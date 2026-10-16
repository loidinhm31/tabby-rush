package dev.doggo.desktop.windows

/**
 * Test double implementing [WindowsToastBridge] for unit and lifecycle tests.
 * Captures dispatched payloads, cleared tags/groups, and simulated failures.
 */
class FakeWindowsToastBridge(
    var available: Boolean = true,
    var shouldFail: Boolean = false,
    var setting: WindowsNotificationSetting = WindowsNotificationSetting.ENABLED
) : WindowsToastBridge {
    val dispatched = mutableListOf<Triple<String, String, String>>()
    val cleared = mutableListOf<Pair<String, String>>()
    var clearedAllCount = 0
    val clearedGroups = mutableListOf<String>()

    override fun isAvailable(): Boolean = available
    override fun querySetting(aumid: String): WindowsNotificationSetting =
        if (available) setting else WindowsNotificationSetting.UNAVAILABLE

    override fun showToast(
        aumid: String,
        xmlPayload: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated bridge failure")
        dispatched.add(Triple(aumid, tag, xmlPayload))
        return WindowsNotificationStatus.Success(tag, group)
    }

    override fun clearToast(
        aumid: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated clear failure")
        cleared.add(Pair(tag, group))
        return WindowsNotificationStatus.Cleared(tag, group)
    }

    override fun clearGroup(aumid: String, group: String): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated clear group failure")
        clearedGroups.add(group)
        return WindowsNotificationStatus.Cleared("", group)
    }

    override fun clearAllToasts(aumid: String): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        clearedAllCount++
        return WindowsNotificationStatus.Cleared("*", "*")
    }
}
