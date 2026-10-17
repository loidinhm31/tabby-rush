package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.NativeReminderAdapter

/**
 * Service Provider Interface (SPI) for desktop native reminder adapters.
 * Target-specific implementations are packaged under src/${desktopTarget}Main and
 * discovered via ServiceLoader.
 */
interface DesktopReminderAdapterProvider {
    /**
     * Target identifier ("linux" or "windows") supported by this provider.
     */
    val target: String

    /**
     * Creates a [NativeReminderAdapter] for the active platform.
     */
    fun createAdapter(): NativeReminderAdapter
}
