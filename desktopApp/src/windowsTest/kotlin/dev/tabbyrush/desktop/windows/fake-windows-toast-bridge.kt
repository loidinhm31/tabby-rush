package dev.tabbyrush.desktop.windows

/**
 * Recorded invocation of a local reminder toast on the test bridge.
 */
data class LocalToastRecord(
    val title: String,
    val body: String,
    val tag: String,
    val group: String
)

/**
 * Recorded invocation of a remote announcement toast on the test bridge.
 */
data class RemoteToastRecord(
    val messageId: String,
    val generation: Long,
    val tag: String,
    val group: String
)

/**
 * Test double implementing [WindowsToastBridge] for unit and lifecycle tests.
 * Captures dispatched local and remote payloads, cleared tags/groups, and simulated callbacks.
 */
class FakeWindowsToastBridge(
    var available: Boolean = true,
    var shouldFail: Boolean = false,
    var setting: WindowsNotificationSetting = WindowsNotificationSetting.ENABLED,
    var failDestroy: Boolean = false
) : WindowsToastBridge {

    // Preserved for backwards compatibility with WindowsReminderAdapterTest
    val dispatched = mutableListOf<Triple<String, String, String>>()

    val localDispatched = mutableListOf<LocalToastRecord>()
    val remoteDispatched = mutableListOf<RemoteToastRecord>()
    val cleared = mutableListOf<Pair<String, String>>()
    val clearedGroups = mutableListOf<String>()
    var destroyCount = 0

    private var callback: ((messageId: String, generation: Long) -> Unit)? = null

    override fun isAvailable(): Boolean = available

    override fun querySetting(aumid: String): WindowsNotificationSetting =
        if (available) setting else WindowsNotificationSetting.UNAVAILABLE

    override fun showLocal(
        title: String,
        body: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated bridge failure")
        if (group == "tr-remote") {
            return WindowsNotificationStatus.Error("Local notifications cannot use reserved group 'tr-remote'")
        }

        localDispatched.add(LocalToastRecord(title, body, tag, group))

        // Populate dispatched tuple for reminder test assertion compatibility
        val safeTitle = escapeXml(title)
        val safeBody = escapeXml(body)
        val xml = """<toast><visual><binding template="ToastGeneric"><text>$safeTitle</text><text>$safeBody</text></binding></visual><audio src="ms-winsoundevent:Notification.Default"/></toast>"""
        dispatched.add(Triple(WindowsNotificationAdapter.AUMID, tag, xml))

        return WindowsNotificationStatus.Success(tag, group)
    }

    override fun showRemote(
        messageId: String,
        generation: Long,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated bridge failure")

        remoteDispatched.add(RemoteToastRecord(messageId, generation, tag, group))
        return WindowsNotificationStatus.Success(tag, group)
    }

    override fun clearToast(
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated clear failure")
        cleared.add(Pair(tag, group))
        return WindowsNotificationStatus.Cleared(tag, group)
    }

    override fun clearGroup(
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated clear group failure")
        clearedGroups.add(group)
        return WindowsNotificationStatus.Cleared("", group)
    }

    override fun registerActivationCallback(
        callback: ((messageId: String, generation: Long) -> Unit)?
    ) {
        this.callback = callback
    }

    override fun destroy(): Boolean {
        destroyCount++
        return !failDestroy
    }

    fun simulateActivation(messageId: String, generation: Long) {
        callback?.invoke(messageId, generation)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
