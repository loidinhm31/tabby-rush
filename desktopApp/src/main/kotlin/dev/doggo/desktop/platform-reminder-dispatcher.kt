package dev.doggo.desktop

import dev.doggo.shared.contract.NativeReminderAdapter
import java.util.ServiceLoader

/**
 * Dispatches and initializes the platform-specific NativeReminderAdapter using Java ServiceLoader.
 *
 * Invariants:
 * 1. Exactly one provider matching the active desktop target must be registered.
 * 2. Zero providers, multiple matching providers, or target mismatches fail closed with an explicit error.
 * 3. Keeps target-specific dependencies strictly isolated.
 */
object PlatformReminderDispatcher {

    fun detectHostTarget(): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> throw IllegalStateException("Unsupported host operating system: '$os'")
        }
    }

    fun resolveProvider(
        target: String = detectHostTarget(),
        classLoader: ClassLoader = PlatformReminderDispatcher::class.java.classLoader
    ): DesktopReminderAdapterProvider {
        val loader = ServiceLoader.load(DesktopReminderAdapterProvider::class.java, classLoader)
        val allProviders = loader.toList()

        val matching = allProviders.filter { it.target == target }

        if (matching.isEmpty()) {
            throw IllegalStateException(
                "No DesktopReminderAdapterProvider found for desktop target '$target'. " +
                    "Available providers: ${allProviders.map { "${it::class.java.name}(${it.target})" }}"
            )
        }

        if (matching.size > 1) {
            throw IllegalStateException(
                "Multiple DesktopReminderAdapterProviders found for desktop target '$target': " +
                    matching.map { it::class.java.name }
            )
        }

        return matching.first()
    }

    fun createAdapter(
        target: String = detectHostTarget(),
        classLoader: ClassLoader = PlatformReminderDispatcher::class.java.classLoader
    ): NativeReminderAdapter {
        val provider = resolveProvider(target, classLoader)
        return provider.createAdapter()
    }
}
