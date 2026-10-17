package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.data.JvmAccountFiles
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DuplicateProviderA : CredentialVaultProvider {
    override val target: String = "duplicate_test"
    override fun createVault(accountFiles: JvmAccountFiles): CredentialVault = throw UnsupportedOperationException()
}

class DuplicateProviderB : CredentialVaultProvider {
    override val target: String = "duplicate_test"
    override fun createVault(accountFiles: JvmAccountFiles): CredentialVault = throw UnsupportedOperationException()
}

class PlatformVaultDispatcherTest {

    @Test
    fun testDetectHostTargetOnLinux() {
        val target = PlatformVaultDispatcher.detectHostTarget()
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (os.contains("linux")) {
            assertEquals("linux", target)
        }
    }

    @Test
    fun testResolveProviderRealLinuxProviderDiscovered() {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (os.contains("linux")) {
            val provider = PlatformVaultDispatcher.resolveProvider("linux")
            assertNotNull(provider)
            assertEquals("linux", provider.target)
            assertEquals("dev.tabbyrush.desktop.linux.LinuxSecretServiceVaultProvider", provider::class.java.name)
        }
    }

    @Test
    fun testResolveProviderNoMatchFailsClosed() {
        val ex = assertFailsWith<IllegalStateException> {
            PlatformVaultDispatcher.resolveProvider("nonexistent_target")
        }
        assertTrue(ex.message!!.contains("No CredentialVaultProvider found for desktop target 'nonexistent_target'"))
    }
    @Test
    fun testResolveProviderUnknownTargetFailsClosed() {
        val ex = assertFailsWith<IllegalStateException> {
            PlatformVaultDispatcher.resolveProvider("unsupported_os")
        }
        assertTrue(ex.message!!.contains("No CredentialVaultProvider found for desktop target 'unsupported_os'"))
    }

    @Test
    fun testResolveProviderMultipleMatchesFailsClosed() {
        val customLoader = object : ClassLoader(PlatformVaultDispatcher::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> {
                if (name == "META-INF/services/dev.tabbyrush.desktop.CredentialVaultProvider") {
                    val descriptor = "${DuplicateProviderA::class.java.name}\n${DuplicateProviderB::class.java.name}\n"
                    val url = URL("mock", "localhost", -1, "/", object : java.net.URLStreamHandler() {
                        override fun openConnection(u: URL): java.net.URLConnection {
                            return object : java.net.URLConnection(u) {
                                override fun connect() {}
                                override fun getInputStream(): InputStream = ByteArrayInputStream(descriptor.toByteArray())
                            }
                        }
                    })
                    return java.util.Collections.enumeration(listOf(url))
                }
                return super.getResources(name)
            }
        }

        val ex = assertFailsWith<IllegalStateException> {
            PlatformVaultDispatcher.resolveProvider("duplicate_test", customLoader)
        }
        assertTrue(ex.message!!.contains("Multiple CredentialVaultProviders found for desktop target 'duplicate_test'"))
    }
}
