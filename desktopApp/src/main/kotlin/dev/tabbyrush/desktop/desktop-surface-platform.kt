package dev.tabbyrush.desktop

import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.Toolkit
import java.util.ServiceLoader

/**
 * Platform abstraction seam for desktop display work areas, system tray capability,
 * and host session metadata.
 *
 * Contract:
 * - monitorWorkAreas: returns connected display work areas in user-space logical dp with insets subtracted.
 * - traySupported: queries native system tray support.
 * - sessionDescription: diagnostic description of the host display session.
 */
interface DesktopSurfacePlatform {
    fun monitorWorkAreas(): List<MonitorWorkArea>
    fun traySupported(): Boolean
    fun sessionDescription(): String
}

/**
 * Service Provider Interface for pluggable platform implementations (e.g. Linux StatusNotifierItem).
 */
interface DesktopSurfacePlatformProvider {
    fun create(): DesktopSurfacePlatform
}

/**
 * Complete default generic AWT platform implementation.
 * Queries [GraphicsEnvironment], [Toolkit] screen insets, and [SystemTray].
 */
open class DefaultAwtDesktopSurfacePlatform : DesktopSurfacePlatform {

    override fun monitorWorkAreas(): List<MonitorWorkArea> {
        if (GraphicsEnvironment.isHeadless()) {
            return emptyList()
        }

        return try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val defaultDevice = try { ge.defaultScreenDevice } catch (_: Throwable) { null }
            val devices = ge.screenDevices ?: return emptyList()

            devices.mapNotNull { device ->
                try {
                    val gc = device.defaultConfiguration ?: return@mapNotNull null
                    val bounds = gc.bounds
                    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
                    val transform = gc.defaultTransform

                    val scaleX = if (transform != null && transform.scaleX > 0.0) transform.scaleX else 1.0
                    val scaleY = if (transform != null && transform.scaleY > 0.0) transform.scaleY else 1.0

                    val xDp = bounds.x + insets.left.toDouble()
                    val yDp = bounds.y + insets.top.toDouble()
                    val widthDp = bounds.width - insets.left - insets.right.toDouble()
                    val heightDp = bounds.height - insets.top - insets.bottom.toDouble()

                    MonitorWorkArea(
                        id = device.getIDstring() ?: "unknown-display",
                        primary = (device == defaultDevice),
                        xDp = xDp,
                        yDp = yDp,
                        widthDp = widthDp,
                        heightDp = heightDp,
                        scaleX = scaleX,
                        scaleY = scaleY
                    )
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override fun traySupported(): Boolean {
        if (GraphicsEnvironment.isHeadless()) {
            return false
        }
        return try {
            SystemTray.isSupported()
        } catch (_: Throwable) {
            false
        }
    }

    override fun sessionDescription(): String {
        val headless = GraphicsEnvironment.isHeadless()
        val os = System.getProperty("os.name") ?: "Unknown"
        val arch = System.getProperty("os.arch") ?: "Unknown"
        val displays = try {
            if (headless) 0 else GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices?.size ?: 0
        } catch (_: Throwable) {
            -1
        }
        return "Generic AWT (OS: $os-$arch, Headless: $headless, Displays: $displays)"
    }
}

/**
 * Dispatcher discovering the active [DesktopSurfacePlatform].
 * Loads via [ServiceLoader] for optional platform providers (e.g. Linux StatusNotifierItem provider),
 * falling back to [DefaultAwtDesktopSurfacePlatform].
 */
object DesktopSurfacePlatformDispatcher {

    @Volatile
    private var testOverride: DesktopSurfacePlatform? = null

    private val discoveredPlatform: DesktopSurfacePlatform by lazy {
        try {
            val loader = ServiceLoader.load(DesktopSurfacePlatformProvider::class.java)
            val provider = loader.firstOrNull()
            provider?.create() ?: DefaultAwtDesktopSurfacePlatform()
        } catch (_: Throwable) {
            DefaultAwtDesktopSurfacePlatform()
        }
    }

    fun current(): DesktopSurfacePlatform {
        return testOverride ?: discoveredPlatform
    }

    fun setForTest(platform: DesktopSurfacePlatform?) {
        testOverride = platform
    }
}
