package dev.tabbyrush.desktop.windows

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Real Windows OS notification settings matching Windows.UI.Notifications.NotificationSetting.
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
 * Replaces obsolete arbitrary-XML and global history clearing with typed local and remote methods.
 */
interface WindowsToastBridge {
    fun isAvailable(): Boolean
    fun querySetting(aumid: String = WindowsNotificationAdapter.AUMID): WindowsNotificationSetting
    fun showLocal(title: String, body: String, tag: String, group: String): WindowsNotificationStatus
    fun showRemote(messageId: String, generation: Long, tag: String, group: String): WindowsNotificationStatus
    fun clearToast(tag: String, group: String): WindowsNotificationStatus
    fun clearGroup(group: String): WindowsNotificationStatus
    fun registerActivationCallback(callback: ((messageId: String, generation: Long) -> Unit)?)
    fun destroy(): Boolean
}

/**
 * JNA binding interface for the owned x64 C++/WinRT native DLL tabbyrush-toast.dll.
 */
interface TabbyRushToastLibrary : Library {
    fun tr_toast_abi_version(): Int
    fun tr_toast_create(
        aumid: String,
        activated: ToastActivatedCallback?,
        user: Pointer?,
        outHandle: LongByReference
    ): Int
    fun tr_toast_setting(handle: Long, outSetting: IntByReference): Int
    fun tr_toast_show(handle: Long, messageId: String, generation: Long, tag: String, group: String): Int
    fun tr_toast_show_local(handle: Long, title: String, body: String, tag: String, group: String): Int
    fun tr_toast_remove(handle: Long, tag: String, group: String): Int
    fun tr_toast_clear_group(handle: Long, group: String): Int
    fun tr_toast_destroy(handle: Long): Int

    interface ToastActivatedCallback : Callback {
        fun invoke(user: Pointer?, messageId: String, generation: Long)
    }
}

/**
 * Safe DLL loader that strictly restricts lookup to verified application paths (no unsafe PATH lookup)
 * and enforces Windows x64 architecture and ABI v1 contract.
 */
object TabbyRushToastLoader {
    private var cachedLibrary: TabbyRushToastLibrary? = null
    private var loadError: String? = null
    private val initLock = Any()

    fun validateAbiVersion(lib: TabbyRushToastLibrary): Boolean {
        return try {
            lib.tr_toast_abi_version() == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun getLibrary(): TabbyRushToastLibrary? {
        synchronized(initLock) {
            if (cachedLibrary != null) return cachedLibrary
            if (loadError != null) return null

            val os = System.getProperty("os.name")?.lowercase() ?: ""
            if (!os.contains("win")) {
                loadError = "Unsupported OS: $os (Windows x64 required)"
                return null
            }

            val arch = System.getProperty("os.arch")?.lowercase() ?: ""
            if (arch != "amd64" && arch != "x86_64") {
                loadError = "Unsupported architecture: $arch (x64 required)"
                return null
            }

            val dllCandidates = mutableListOf<File>()

            // 1. Explicit test or environment override
            System.getProperty("tabbyrush.toast.dll.path")?.let { dllCandidates.add(File(it)) }

            // 2. Compose application resources directory
            System.getProperty("compose.application.resources.dir")?.let {
                dllCandidates.add(File(it, "tabbyrush-toast.dll"))
            }

            // 3. JPackage / Distributable application installation directory
            val javaHome = System.getProperty("java.home")
            if (javaHome != null) {
                val appRoot = File(javaHome).parentFile
                if (appRoot != null) {
                    dllCandidates.add(File(appRoot, "tabbyrush-toast.dll"))
                    dllCandidates.add(File(appRoot, "app/tabbyrush-toast.dll"))
                }
            }

            var resolvedFile: File? = dllCandidates.firstOrNull { it.exists() && it.isFile }

            // 4. Bundled resource in JAR classpath extracted safely
            if (resolvedFile == null) {
                try {
                    resolvedFile = Native.extractFromResourcePath("/win32-x86-64/tabbyrush-toast.dll")
                } catch (_: Throwable) {}
            }

            if (resolvedFile == null || !resolvedFile.exists()) {
                loadError = "tabbyrush-toast.dll not found in verified application paths"
                return null
            }

            try {
                val lib = Native.load(resolvedFile.absolutePath, TabbyRushToastLibrary::class.java)
                if (!validateAbiVersion(lib)) {
                    val version = try { lib.tr_toast_abi_version() } catch (_: Throwable) { -1 }
                    loadError = "ABI mismatch: expected version 1, got $version"
                    return null
                }
                cachedLibrary = lib
                return lib
            } catch (t: Throwable) {
                loadError = "Failed to load tabbyrush-toast.dll: ${t.message}"
                return null
            }
        }
    }

    fun getLoadError(): String? = loadError
}

/**
 * State retained when a native handle destruction times out, preventing garbage collection
 * of the native callback trampoline or library mapping while native callbacks might still fire.
 */
data class RetainedHandleState(
    val handle: Long,
    val library: TabbyRushToastLibrary,
    val callback: TabbyRushToastLibrary.ToastActivatedCallback
)

/**
 * Real Windows Runtime (WinRT) toast bridge using JNA native binding to tabbyrush-toast.dll.
 * Operates with AppUserModelId (AUMID) and tag/group scoping for Windows 10 and 11.
 */
class JnaWindowsToastBridge(
    val aumid: String = WindowsNotificationAdapter.AUMID,
    private val libraryOverride: TabbyRushToastLibrary? = null
) : WindowsToastBridge {

    private val library: TabbyRushToastLibrary? = libraryOverride ?: TabbyRushToastLoader.getLibrary()
    private val handle = AtomicLong(0)
    private val isDestroyed = AtomicBoolean(false)
    private val rwLock = ReentrantReadWriteLock()

    @Volatile
    private var activationListener: ((messageId: String, generation: Long) -> Unit)? = null

    // Strongly pin callback instance to prevent premature garbage collection by JVM
    private val nativeCallback = object : TabbyRushToastLibrary.ToastActivatedCallback {
        override fun invoke(user: Pointer?, messageId: String, generation: Long) {
            if (isDestroyed.get()) return
            // Copy borrowed string immediately across JNA boundary
            val safeMessageId = String(messageId.toCharArray())
            activationListener?.invoke(safeMessageId, generation)
        }
    }

    companion object {
        // Retain full reference state for handles that failed teardown (timeout) so JNA does not free native memory
        private val retainedFailedStates = CopyOnWriteArrayList<RetainedHandleState>()

        fun getRetainedFailedStateCount(): Int = retainedFailedStates.size
    }

    init {
        val lib = library
        if (lib != null) {
            // Strictly check ABI version before invoking create
            if (TabbyRushToastLoader.validateAbiVersion(lib)) {
                try {
                    val outHandle = LongByReference(0)
                    val hr = lib.tr_toast_create(aumid, nativeCallback, null, outHandle)
                    if (hr == 0 && outHandle.value != 0L) {
                        handle.set(outHandle.value)
                    }
                } catch (_: Throwable) {
                    handle.set(0)
                }
            } else {
                handle.set(0)
            }
        }
    }

    override fun isAvailable(): Boolean = library != null && handle.get() != 0L && !isDestroyed.get()

    override fun querySetting(aumid: String): WindowsNotificationSetting {
        val lock = rwLock.readLock()
        lock.lock()
        try {
            val lib = library ?: return WindowsNotificationSetting.UNAVAILABLE
            val h = handle.get()
            if (h == 0L || isDestroyed.get()) return WindowsNotificationSetting.UNAVAILABLE

            val outSetting = IntByReference(-1)
            val hr = lib.tr_toast_setting(h, outSetting)
            if (hr != 0) return WindowsNotificationSetting.UNAVAILABLE

            return when (outSetting.value) {
                0 -> WindowsNotificationSetting.ENABLED
                1 -> WindowsNotificationSetting.DISABLED_FOR_APPLICATION
                2 -> WindowsNotificationSetting.DISABLED_FOR_USER
                3 -> WindowsNotificationSetting.DISABLED_BY_GROUP_POLICY
                4 -> WindowsNotificationSetting.DISABLED_BY_MANIFEST
                else -> WindowsNotificationSetting.UNAVAILABLE
            }
        } finally {
            lock.unlock()
        }
    }

    override fun showLocal(
        title: String,
        body: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        val lock = rwLock.readLock()
        lock.lock()
        try {
            if (!isAvailable()) {
                return WindowsNotificationStatus.Unavailable(
                    TabbyRushToastLoader.getLoadError() ?: "Native toast bridge unavailable"
                )
            }

            // Contract requirement: showLocal strictly rejects group "tr-remote"
            if (group == "tr-remote") {
                return WindowsNotificationStatus.Error("Local notifications cannot use reserved group 'tr-remote'")
            }

            val lib = library ?: return WindowsNotificationStatus.Unavailable("Library not loaded")
            val h = handle.get()

            val hr = lib.tr_toast_show_local(h, title, body, tag, group)
            return if (hr == 0) {
                WindowsNotificationStatus.Success(tag, group)
            } else {
                WindowsNotificationStatus.Error("Native tr_toast_show_local failed: HRESULT $hr")
            }
        } finally {
            lock.unlock()
        }
    }

    override fun showRemote(
        messageId: String,
        generation: Long,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        val lock = rwLock.readLock()
        lock.lock()
        try {
            if (!isAvailable()) {
                return WindowsNotificationStatus.Unavailable(
                    TabbyRushToastLoader.getLoadError() ?: "Native toast bridge unavailable"
                )
            }

            val lib = library ?: return WindowsNotificationStatus.Unavailable("Library not loaded")
            val h = handle.get()

            val hr = lib.tr_toast_show(h, messageId, generation, tag, group)
            return if (hr == 0) {
                WindowsNotificationStatus.Success(tag, group)
            } else {
                WindowsNotificationStatus.Error("Native tr_toast_show failed: HRESULT $hr")
            }
        } finally {
            lock.unlock()
        }
    }

    override fun clearToast(
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        val lock = rwLock.readLock()
        lock.lock()
        try {
            if (!isAvailable()) {
                return WindowsNotificationStatus.Unavailable("Native toast bridge unavailable")
            }

            val lib = library ?: return WindowsNotificationStatus.Unavailable("Library not loaded")
            val h = handle.get()

            val hr = lib.tr_toast_remove(h, tag, group)
            return if (hr == 0) {
                WindowsNotificationStatus.Cleared(tag, group)
            } else {
                WindowsNotificationStatus.Error("Native tr_toast_remove failed: HRESULT $hr")
            }
        } finally {
            lock.unlock()
        }
    }

    override fun clearGroup(
        group: String
    ): WindowsNotificationStatus {
        val lock = rwLock.readLock()
        lock.lock()
        try {
            if (!isAvailable()) {
                return WindowsNotificationStatus.Unavailable("Native toast bridge unavailable")
            }

            val lib = library ?: return WindowsNotificationStatus.Unavailable("Library not loaded")
            val h = handle.get()

            val hr = lib.tr_toast_clear_group(h, group)
            return if (hr == 0) {
                WindowsNotificationStatus.Cleared("", group)
            } else {
                WindowsNotificationStatus.Error("Native tr_toast_clear_group failed: HRESULT $hr")
            }
        } finally {
            lock.unlock()
        }
    }

    override fun registerActivationCallback(
        callback: ((messageId: String, generation: Long) -> Unit)?
    ) {
        activationListener = callback
    }

    override fun destroy(): Boolean {
        val lock = rwLock.writeLock()
        lock.lock()
        try {
            if (isDestroyed.compareAndSet(false, true)) {
                val h = handle.getAndSet(0)
                val lib = library
                if (h != 0L && lib != null) {
                    val hr = lib.tr_toast_destroy(h)
                    if (hr != 0) {
                        // Teardown or drain timed out: retain full state (handle, library, callback trampoline)
                        // to prevent premature GC while native callbacks might still fire.
                        retainedFailedStates.add(RetainedHandleState(h, lib, nativeCallback))
                        return false
                    }
                }
            }
            return true
        } finally {
            lock.unlock()
        }
    }
}
