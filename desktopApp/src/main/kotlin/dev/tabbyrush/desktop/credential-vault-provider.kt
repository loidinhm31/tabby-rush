package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.data.JvmAccountFiles

/**
 * Service Provider Interface (SPI) for desktop hardware-backed credential vaults.
 * Target-specific implementations are packaged under src/${desktopTarget}Main and
 * discovered via ServiceLoader.
 */
interface CredentialVaultProvider {
    /**
     * Target identifier ("linux" or "windows") supported by this provider.
     */
    val target: String

    /**
     * Creates a hardware-backed [CredentialVault] for the current profile.
     * Fails closed if hardware custody is unavailable; never falls back to plaintext.
     */
    fun createVault(accountFiles: JvmAccountFiles): CredentialVault
}
