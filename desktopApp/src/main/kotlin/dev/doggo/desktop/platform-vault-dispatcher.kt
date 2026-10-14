package dev.doggo.desktop

import dev.doggo.shared.contract.CredentialVault
import dev.doggo.shared.data.JvmAccountFiles
import java.util.ServiceLoader

/**
 * Dispatches and initializes the platform-specific CredentialVault using Java ServiceLoader.
 *
 * Invariants:
 * 1. Exactly one provider matching the active desktop target must be registered.
 * 2. Zero providers, multiple matching providers, or target mismatches fail closed with an explicit error.
 * 3. Never falls back to unencrypted or plaintext token storage.
 */
object PlatformVaultDispatcher {

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
        classLoader: ClassLoader = PlatformVaultDispatcher::class.java.classLoader
    ): CredentialVaultProvider {
        val loader = ServiceLoader.load(CredentialVaultProvider::class.java, classLoader)
        val allProviders = loader.toList()

        val matching = allProviders.filter { it.target == target }

        if (matching.isEmpty()) {
            throw IllegalStateException(
                "No CredentialVaultProvider found for desktop target '$target'. " +
                    "Available providers: ${allProviders.map { "${it::class.java.name}(${it.target})" }}"
            )
        }

        if (matching.size > 1) {
            throw IllegalStateException(
                "Multiple CredentialVaultProviders found for desktop target '$target': " +
                    matching.map { it::class.java.name }
            )
        }

        return matching.first()
    }

    fun createVault(
        accountFiles: JvmAccountFiles,
        target: String = detectHostTarget(),
        classLoader: ClassLoader = PlatformVaultDispatcher::class.java.classLoader
    ): CredentialVault {
        val provider = resolveProvider(target, classLoader)
        return provider.createVault(accountFiles)
    }
}
