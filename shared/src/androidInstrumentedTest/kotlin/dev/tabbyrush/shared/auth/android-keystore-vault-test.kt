package dev.tabbyrush.shared.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.VaultStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreVaultTest {

    private lateinit var context: Context
    private lateinit var vault: AndroidKeystoreVault

    private val accountKeyA = AccountKey("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    private val accountKeyB = AccountKey("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        vault = AndroidKeystoreVault(context)
        runBlocking {
            vault.clear(accountKeyA)
            vault.clear(accountKeyB)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            vault.clear(accountKeyA)
            vault.clear(accountKeyB)
        }
    }

    @Test
    fun testStoreAndLoadCredentials() = runBlocking {
        val originalBundle = CredentialBundle(
            accessToken = "test-access-token-12345",
            refreshToken = "test-refresh-token-67890"
        )

        vault.store(accountKeyA, originalBundle)
        assertEquals(VaultStatus.READY, vault.status)

        val loadedBundle = vault.load(accountKeyA)
        assertNotNull(loadedBundle)
        assertEquals("test-access-token-12345", loadedBundle?.accessToken)
        assertEquals("test-refresh-token-67890", loadedBundle?.refreshToken)
        assertEquals(VaultStatus.READY, vault.status)
    }

    @Test
    fun testFreshNoncePerWrite() = runBlocking {
        val bundle1 = CredentialBundle("acc1", "ref1")
        val bundle2 = CredentialBundle("acc2", "ref2")

        vault.store(accountKeyA, bundle1)
        val vaultDir = File(context.filesDir, "tabbyrush_vault")
        val file = File(vaultDir, "account_${accountKeyA.value}.vault")
        val bytes1 = file.readBytes()
        val nonce1 = bytes1.sliceArray(4 until 16)

        vault.store(accountKeyA, bundle2)
        val bytes2 = file.readBytes()
        val nonce2 = bytes2.sliceArray(4 until 16)

        assertFalse("Fresh 12-byte nonce must be generated per store", nonce1.contentEquals(nonce2))
    }

    @Test
    fun testAadBindingPreventsCrossAccountDecryption() = runBlocking {
        val bundleA = CredentialBundle("secret-acc-A", "secret-ref-A")
        val bundleB = CredentialBundle("secret-acc-B", "secret-ref-B")
        vault.store(accountKeyA, bundleA)
        vault.store(accountKeyB, bundleB) // Ensure Account B has an initialized KeyStore key

        val vaultDir = File(context.filesDir, "tabbyrush_vault")
        val fileA = File(vaultDir, "account_${accountKeyA.value}.vault")
        val fileB = File(vaultDir, "account_${accountKeyB.value}.vault")

        // Copy Account A's ciphertext file directly over Account B's path
        fileA.copyTo(fileB, overwrite = true)

        try {
            // Decrypting under Account B must fail because AAD binds to Account A's key
            vault.load(accountKeyB)
            fail("Expected decryption failure due to AAD mismatch")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Failed to decrypt") == true || e is IllegalStateException)
            assertEquals(VaultStatus.CORRUPT, vault.status)
        }
    }

    @Test
    fun testCorruptMagicHeaderDetection() = runBlocking {
        val bundle = CredentialBundle("access", "refresh")
        vault.store(accountKeyA, bundle)

        val vaultDir = File(context.filesDir, "tabbyrush_vault")
        val file = File(vaultDir, "account_${accountKeyA.value}.vault")
        val bytes = file.readBytes()

        // Tamper magic header
        bytes[0] = 0x00
        file.writeBytes(bytes)

        try {
            vault.load(accountKeyA)
            fail("Expected header magic mismatch failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("header magic mismatch") == true)
            assertEquals(VaultStatus.CORRUPT, vault.status)
        }
    }

    @Test
    fun testTruncatedVaultFileDetection() = runBlocking {
        val vaultDir = File(context.filesDir, "tabbyrush_vault")
        val file = File(vaultDir, "account_${accountKeyA.value}.vault")
        file.writeBytes(byteArrayOf(0x44, 0x47, 0x56, 0x31)) // Only 4 bytes

        try {
            vault.load(accountKeyA)
            fail("Expected truncated header error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("truncated header") == true)
            assertEquals(VaultStatus.CORRUPT, vault.status)
        }
    }

    @Test
    fun testTamperedCiphertextDetection() = runBlocking {
        val bundle = CredentialBundle("valid-access", "valid-refresh")
        vault.store(accountKeyA, bundle)

        val vaultDir = File(context.filesDir, "tabbyrush_vault")
        val file = File(vaultDir, "account_${accountKeyA.value}.vault")
        val bytes = file.readBytes()

        // Flip bits in ciphertext / tag
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes)

        try {
            vault.load(accountKeyA)
            fail("Expected authentication tag failure on tampered ciphertext")
        } catch (e: Exception) {
            assertEquals(VaultStatus.CORRUPT, vault.status)
        }
    }

    @Test
    fun testClearDeletesFileAndAlias() = runBlocking {
        val bundle = CredentialBundle("acc", "ref")
        vault.store(accountKeyA, bundle)

        val vaultDir = File(context.filesDir, "tabbyrush_vault")
        val file = File(vaultDir, "account_${accountKeyA.value}.vault")
        assertTrue(file.exists())

        vault.clear(accountKeyA)
        assertFalse(file.exists())

        val reloaded = vault.load(accountKeyA)
        assertNull(reloaded)
        assertEquals(VaultStatus.READY, vault.status)
    }
}
