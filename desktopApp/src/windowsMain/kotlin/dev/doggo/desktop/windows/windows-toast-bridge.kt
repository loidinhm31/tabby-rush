package dev.doggo.desktop.windows

import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Real Windows OS notification settings from Windows.UI.Notifications.NotificationSetting.
 */
enum class WindowsNotificationSetting {
    ENABLED,
    DISABLED_FOR_APPLICATION,
    DISABLED_FOR_USER,
    DISABLED_BY_GROUP_POLICY,
    DISABLED_BY_MANIFEST,
    UNAVAILABLE
}

/**
 * Interface abstracting Windows toast notification delivery, settings query, and history removal.
 */
interface WindowsToastBridge {
    fun isAvailable(): Boolean
    fun querySetting(aumid: String): WindowsNotificationSetting
    fun showToast(aumid: String, xmlPayload: String, tag: String, group: String): WindowsNotificationStatus
    fun clearToast(aumid: String, tag: String, group: String): WindowsNotificationStatus
    fun clearGroup(aumid: String, group: String): WindowsNotificationStatus
    fun clearAllToasts(aumid: String): WindowsNotificationStatus
}

/**
 * Windows Runtime (WinRT) toast bridge using PowerShell Windows Runtime projection.
 * Operates with AppUserModelId (AUMID) and tag/group scoping for Windows 10 and 11.
 */
class PowerShellWindowsToastBridge(
    private val processExecutor: (String, Long) -> Pair<Int, String> = ::defaultExecutePowerShell
) : WindowsToastBridge {

    private val available: Boolean by lazy {
        try {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            if (!os.contains("win")) return@lazy false
            val script = """
                ${'$'}ProgressPreference = 'SilentlyContinue'
                ${'$'}ErrorActionPreference = 'Stop'
                [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
            """.trimIndent()
            val (exit, _) = processExecutor(script, 3)
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    override fun isAvailable(): Boolean = available

    private val settingCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, WindowsNotificationSetting>>()

    override fun querySetting(aumid: String): WindowsNotificationSetting {
        if (!isAvailable()) return WindowsNotificationSetting.UNAVAILABLE
        val nowNano = System.nanoTime()
        val cached = settingCache[aumid]
        if (cached != null && (nowNano - cached.first) < 2_000_000_000L) {
            return cached.second
        }
        val safeAumid = aumid.replace("'", "''")
        val script = """
            ${'$'}ProgressPreference = 'SilentlyContinue'
            ${'$'}ErrorActionPreference = 'Stop'
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            ${'$'}notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$safeAumid')
            Write-Output ${'$'}notifier.Setting.ToString()
        """.trimIndent()

        val (exit, output) = processExecutor(script, 3)
        if (exit != 0) return WindowsNotificationSetting.UNAVAILABLE
        val token = output.lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it == "Enabled" ||
                it == "DisabledForApplication" ||
                it == "DisabledForUser" ||
                it == "DisabledByGroupPolicy" ||
                it == "DisabledByManifest"
            }
        val resolved = when (token) {
            "Enabled" -> WindowsNotificationSetting.ENABLED
            "DisabledForApplication" -> WindowsNotificationSetting.DISABLED_FOR_APPLICATION
            "DisabledForUser" -> WindowsNotificationSetting.DISABLED_FOR_USER
            "DisabledByGroupPolicy" -> WindowsNotificationSetting.DISABLED_BY_GROUP_POLICY
            "DisabledByManifest" -> WindowsNotificationSetting.DISABLED_BY_MANIFEST
            else -> WindowsNotificationSetting.UNAVAILABLE
        }
        settingCache[aumid] = nowNano to resolved
        return resolved
    }

    override fun showToast(
        aumid: String,
        xmlPayload: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!isAvailable()) {
            return WindowsNotificationStatus.Unavailable("Windows Runtime toast notifications unavailable on this platform")
        }
        val safeXml = xmlPayload.replace("'", "''")
        val safeAumid = aumid.replace("'", "''")
        val safeTag = tag.replace("'", "''")
        val safeGroup = group.replace("'", "''")

        val script = """
            ${'$'}ProgressPreference = 'SilentlyContinue'
            ${'$'}ErrorActionPreference = 'Stop'
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
            ${'$'}xmlDoc = [Windows.Data.Xml.Dom.XmlDocument]::new()
            ${'$'}xmlDoc.LoadXml('$safeXml')
            ${'$'}toast = [Windows.UI.Notifications.ToastNotification]::new(${'$'}xmlDoc)
            ${'$'}toast.Tag = '$safeTag'
            ${'$'}toast.Group = '$safeGroup'
            ${'$'}toast.Priority = [Windows.UI.Notifications.ToastNotificationPriority]::High
            ${'$'}notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$safeAumid')
            ${'$'}notifier.Show(${'$'}toast)
        """.trimIndent()

        val (exit, output) = processExecutor(script, 5)
        return if (exit == 0) {
            WindowsNotificationStatus.Success(tag, group)
        } else {
            WindowsNotificationStatus.Error("Failed to show toast (exit $exit): $output")
        }
    }

    override fun clearToast(
        aumid: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!isAvailable()) return WindowsNotificationStatus.Unavailable("Windows Runtime toast notifications unavailable")
        val safeAumid = aumid.replace("'", "''")
        val safeTag = tag.replace("'", "''")
        val safeGroup = group.replace("'", "''")

        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.UI.Notifications.ToastNotificationManager]::History.Remove('$safeTag', '$safeGroup', '$safeAumid')
        """.trimIndent()

        val (exit, output) = processExecutor(script, 4)
        return if (exit == 0) {
            WindowsNotificationStatus.Cleared(tag, group)
        } else {
            WindowsNotificationStatus.Error("Failed to clear toast (exit $exit): $output")
        }
    }

    override fun clearGroup(aumid: String, group: String): WindowsNotificationStatus {
        if (!isAvailable()) return WindowsNotificationStatus.Unavailable("Windows Runtime toast notifications unavailable")
        val safeAumid = aumid.replace("'", "''")
        val safeGroup = group.replace("'", "''")

        val script = """
            ${'$'}ProgressPreference = 'SilentlyContinue'
            ${'$'}ErrorActionPreference = 'Stop'
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.UI.Notifications.ToastNotificationManager]::History.RemoveGroup('$safeGroup', '$safeAumid')
        """.trimIndent()

        val (exit, output) = processExecutor(script, 4)
        return if (exit == 0) {
            WindowsNotificationStatus.Cleared("", group)
        } else {
            WindowsNotificationStatus.Error("Failed to clear group (exit $exit): $output")
        }
    }

    override fun clearAllToasts(aumid: String): WindowsNotificationStatus {
        if (!isAvailable()) return WindowsNotificationStatus.Unavailable("Windows Runtime toast notifications unavailable")
        val safeAumid = aumid.replace("'", "''")

        val script = """
            ${'$'}ProgressPreference = 'SilentlyContinue'
            ${'$'}ErrorActionPreference = 'Stop'
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.UI.Notifications.ToastNotificationManager]::History.Clear('$safeAumid')
        """.trimIndent()

        val (exit, output) = processExecutor(script, 4)
        return if (exit == 0) {
            WindowsNotificationStatus.Cleared("*", "*")
        } else {
            WindowsNotificationStatus.Error("Failed to clear all toasts (exit $exit): $output")
        }
    }

    companion object {
        fun defaultExecutePowerShell(script: String, timeoutSec: Long = 5): Pair<Int, String> {
            return try {
                val bytes = script.toByteArray(Charsets.UTF_16LE)
                val base64 = Base64.getEncoder().encodeToString(bytes)
                val process = ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-EncodedCommand",
                    base64
                ).start()

                val stdoutFuture = CompletableFuture.supplyAsync {
                    try {
                        process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (_: Exception) { "" }
                }
                val stderrFuture = CompletableFuture.supplyAsync {
                    try {
                        process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (_: Exception) { "" }
                }

                val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    stdoutFuture.cancel(true)
                    stderrFuture.cancel(true)
                    -1 to "PowerShell execution timed out after ${timeoutSec}s"
                } else {
                    val stdout = try { stdoutFuture.get(1, TimeUnit.SECONDS).trim() } catch (_: Exception) { "" }
                    val stderr = try { stderrFuture.get(1, TimeUnit.SECONDS).trim() } catch (_: Exception) { "" }
                    val exit = process.exitValue()
                    val output = if (exit == 0) stdout else stderr.ifBlank { stdout }
                    exit to output
                }
            } catch (e: Exception) {
                -1 to (e.message ?: "Process execution failed")
            }
        }
    }
}

/**
 * Typealias preserving compatibility with earlier Phase 09 references.
 */
typealias JnaWindowsToastBridge = PowerShellWindowsToastBridge
