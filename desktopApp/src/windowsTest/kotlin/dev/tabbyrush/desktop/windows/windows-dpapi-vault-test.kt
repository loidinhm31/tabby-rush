package dev.tabbyrush.desktop.windows

import dev.tabbyrush.desktop.PlatformVaultDispatcher
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.VaultStatus
import dev.tabbyrush.shared.data.JvmAccountFiles
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.AclEntryType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeWindowsDpapiProtector(
    var available: Boolean = true,
    var failUnprotect: Boolean = false
) : WindowsDpapiProtector {
    private val xorKey: Byte = 0x5A

    override fun isAvailable(): Boolean = available

    override fun protect(plainBytes: ByteArray): ByteArray {
        if (!available) throw IllegalStateException("DPAPI not available")
        return ByteArray(plainBytes.size) { i -> (plainBytes[i].toInt() xor xorKey.toInt()).toByte() }
    }

    override fun unprotect(cipherBytes: ByteArray): ByteArray {
        if (!available) throw IllegalStateException("DPAPI not available")
        if (failUnprotect) throw RuntimeException("Simulated Win32 CryptUnprotectData decryption failure")
        return ByteArray(cipherBytes.size) { i -> (cipherBytes[i].toInt() xor xorKey.toInt()).toByte() }
    }
}

class WindowsDpapiVaultTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-windows-vault-test").toFile()
        accountFiles = JvmAccountFiles(baseDir = tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testStoreAndLoadRoundTrip() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        assertEquals(VaultStatus.READY, vault.status)

        val bundle = CredentialBundle(accessToken = "access-token-123", refreshToken = "refresh-token-456")
        vault.store(testAccountKey, bundle)

        val loaded = vault.load(testAccountKey)
        assertNotNull(loaded)
        assertEquals("access-token-123", loaded.accessToken)
        assertEquals("refresh-token-456", loaded.refreshToken)
        assertEquals(VaultStatus.READY, vault.status)
    }

    @Test
    fun testAtomicReplacement() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val bundle1 = CredentialBundle(accessToken = "token-v1", refreshToken = "refresh-v1")
        vault.store(testAccountKey, bundle1)

        val loaded1 = vault.load(testAccountKey)
        assertNotNull(loaded1)
        assertEquals("token-v1", loaded1.accessToken)

        val bundle2 = CredentialBundle(accessToken = "token-v2", refreshToken = "refresh-v2")
        vault.store(testAccountKey, bundle2)

        val loaded2 = vault.load(testAccountKey)
        assertNotNull(loaded2)
        assertEquals("token-v2", loaded2.accessToken)
        assertEquals("refresh-v2", loaded2.refreshToken)
    }

    @Test
    fun testMissingSecretReturnsNull() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val loaded = vault.load(testAccountKey)
        assertNull(loaded)
        assertEquals(VaultStatus.READY, vault.status)
    }

    @Test
    fun testClearRemovesVaultFile() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val bundle = CredentialBundle(accessToken = "to-be-deleted", refreshToken = "refresh-del")
        vault.store(testAccountKey, bundle)

        val vaultFile = vault.getVaultFile(testAccountKey)
        assertTrue(vaultFile.exists(), "Vault file should exist before clear")

        vault.clear(testAccountKey)
        assertTrue(!vaultFile.exists(), "Vault file must be deleted after clear")

        val loaded = vault.load(testAccountKey)
        assertNull(loaded)
    }

    @Test
    fun testCorruptPayloadFailsClosed() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val vaultFile = vault.getVaultFile(testAccountKey)
        vaultFile.parentFile.mkdirs()
        // Write corrupt cipher bytes that decrypt to invalid JSON
        val corruptCipher = fakeProtector.protect("invalid-corrupt-json-payload".toByteArray(Charsets.UTF_8))
        vaultFile.writeBytes(corruptCipher)

        assertFailsWith<IllegalStateException> {
            vault.load(testAccountKey)
        }
        assertEquals(VaultStatus.CORRUPT, vault.status)
    }

    @Test
    fun testUnprotectFailureTransitionsToCorrupt() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector(failUnprotect = true)
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val vaultFile = vault.getVaultFile(testAccountKey)
        vaultFile.parentFile.mkdirs()
        vaultFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val ex = assertFailsWith<IllegalStateException> {
            vault.load(testAccountKey)
        }
        assertTrue(ex.message?.contains("Failed to unprotect DPAPI credential bundle") == true)
        assertEquals(VaultStatus.CORRUPT, vault.status)
    }

    @Test
    fun testAccountKeyMismatchFailsClosed() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val wrongAccountKey = AccountKey("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val bundle = CredentialBundle(accessToken = "token-xyz", refreshToken = "refresh-xyz")
        vault.store(wrongAccountKey, bundle)

        // Point the vault file of testAccountKey to the cipher of wrongAccountKey
        val wrongFile = vault.getVaultFile(wrongAccountKey)
        val targetFile = vault.getVaultFile(testAccountKey)
        targetFile.parentFile.mkdirs()
        wrongFile.copyTo(targetFile, overwrite = true)

        val ex = assertFailsWith<IllegalStateException> {
            vault.load(testAccountKey)
        }
        assertTrue(ex.message?.contains("Account key mismatch") == true)
        assertEquals(VaultStatus.CORRUPT, vault.status)
    }

    @Test
    fun testUnavailableProtectorThrows() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector(available = false)
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        assertEquals(VaultStatus.UNAVAILABLE, vault.status)

        assertFailsWith<IllegalStateException> {
            vault.store(testAccountKey, CredentialBundle("a", "b"))
        }

        assertFailsWith<IllegalStateException> {
            vault.load(testAccountKey)
        }
    }

    @Test
    fun testServiceLoaderDiscoversWindowsProvider() {
        val provider = PlatformVaultDispatcher.resolveProvider("windows")
        assertEquals("windows", provider.target)
        assertTrue(provider is WindowsDpapiVaultProvider)

        val vault = provider.createVault(accountFiles)
        assertTrue(vault is WindowsDpapiVault)
    }

    @Test
    fun testLinuxProviderAbsentOnWindows() {
        val ex = assertFailsWith<IllegalStateException> {
            PlatformVaultDispatcher.resolveProvider("linux")
        }
        assertTrue(ex.message!!.contains("No CredentialVaultProvider found for desktop target 'linux'"))
    }

    @Test
    fun testLiveJnaDpapiRoundtripOnWindows() = runTest {
        val protector = JnaWindowsDpapiProtector()
        if (!protector.isAvailable()) {
            return@runTest
        }

        val vault = WindowsDpapiVault(accountFiles, protector)
        val originalBundle = CredentialBundle(
            accessToken = "live-jna-access-token-99999",
            refreshToken = "live-jna-refresh-token-88888"
        )

        vault.store(testAccountKey, originalBundle)
        val loaded = vault.load(testAccountKey)
        assertNotNull(loaded)
        assertEquals(originalBundle.accessToken, loaded.accessToken)
        assertEquals(originalBundle.refreshToken, loaded.refreshToken)

        vault.clear(testAccountKey)
        val afterClear = vault.load(testAccountKey)
        assertNull(afterClear)
    }

    @Test
    fun testAclEnforcementOnStoredVaultFile() = runTest {
        val fakeProtector = FakeWindowsDpapiProtector()
        val vault = WindowsDpapiVault(accountFiles, fakeProtector)

        val bundle = CredentialBundle(accessToken = "acl-test-token", refreshToken = "acl-test-refresh")
        vault.store(testAccountKey, bundle)

        val vaultFile = vault.getVaultFile(testAccountKey)
        assertTrue(vaultFile.exists(), "Vault file must exist")

        val path = vaultFile.toPath()
        if (path.fileSystem.supportedFileAttributeViews().contains("acl")) {
            val aclView = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            assertNotNull(aclView, "AclFileAttributeView must not be null on ACL-supporting filesystem")
            val owner = aclView.owner
            assertNotNull(owner, "Owner principal must not be null")
            val acl = aclView.acl
            assertNotNull(acl, "ACL list must not be null")
            assertTrue(acl.isNotEmpty(), "ACL must contain entries")
            val ownerEntry = acl.firstOrNull { it.principal() == owner }
            assertNotNull(ownerEntry, "ACL must contain an entry for the owner principal")
            assertEquals(AclEntryType.ALLOW, ownerEntry.type(), "Owner entry must be ALLOW")
        }
    }
}
