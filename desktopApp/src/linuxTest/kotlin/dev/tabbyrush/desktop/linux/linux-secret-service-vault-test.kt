package dev.tabbyrush.desktop.linux

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.VaultStatus
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSecretServiceClient : SecretServiceClient {
    var simulatedStatus: VaultStatus = VaultStatus.READY
    val secrets = ConcurrentHashMap<String, String>()

    override fun checkStatus(): VaultStatus = simulatedStatus

    override fun storeSecret(service: String, account: String, label: String, secret: String) {
        if (simulatedStatus != VaultStatus.READY) {
            throw IllegalStateException("Secret Service unavailable (status=$simulatedStatus)")
        }
        secrets["$service:$account"] = secret
    }

    override fun lookupSecret(service: String, account: String): String? {
        if (simulatedStatus != VaultStatus.READY) {
            throw IllegalStateException("Secret Service unavailable (status=$simulatedStatus)")
        }
        return secrets["$service:$account"]
    }

    override fun clearSecret(service: String, account: String) {
        secrets.remove("$service:$account")
    }
}

class LinuxSecretServiceVaultTest {

    private val accountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    @Test
    fun testVaultStatusTransitions() {
        val client = FakeSecretServiceClient()
        val vault = LinuxSecretServiceVault(client)

        client.simulatedStatus = VaultStatus.READY
        assertEquals(VaultStatus.READY, vault.status)

        client.simulatedStatus = VaultStatus.LOCKED
        assertEquals(VaultStatus.LOCKED, vault.status)

        client.simulatedStatus = VaultStatus.UNAVAILABLE
        assertEquals(VaultStatus.UNAVAILABLE, vault.status)
    }

    @Test
    fun testAtomicStoreAndLoad() = runTest {
        val client = FakeSecretServiceClient()
        val vault = LinuxSecretServiceVault(client)

        val bundle = CredentialBundle(accessToken = "at_initial_token", refreshToken = "rt_initial_token")
        vault.store(accountKey, bundle)

        // Verify underlying storage is a single atomic JSON bundle
        val raw = client.secrets["tabbyrush:${accountKey.value}"]
        assertNotNull(raw)
        assertTrue(raw.contains("at_initial_token"))
        assertTrue(raw.contains("rt_initial_token"))

        val loaded = vault.load(accountKey)
        assertNotNull(loaded)
        assertEquals("at_initial_token", loaded.accessToken)
        assertEquals("rt_initial_token", loaded.refreshToken)
    }

    @Test
    fun testAtomicReplacement() = runTest {
        val client = FakeSecretServiceClient()
        val vault = LinuxSecretServiceVault(client)

        vault.store(accountKey, CredentialBundle("at_old", "rt_old"))
        vault.store(accountKey, CredentialBundle("at_new", "rt_new"))

        val loaded = vault.load(accountKey)
        assertNotNull(loaded)
        assertEquals("at_new", loaded.accessToken)
        assertEquals("rt_new", loaded.refreshToken)
    }

    @Test
    fun testCorruptPayloadFailsClosed() = runTest {
        val client = FakeSecretServiceClient()
        val vault = LinuxSecretServiceVault(client)

        client.secrets["tabbyrush:${accountKey.value}"] = "{ malformed json: not valid"

        assertFailsWith<IllegalStateException> {
            vault.load(accountKey)
        }
    }

    @Test
    fun testMissingSecretReturnsNull() = runTest {
        val client = FakeSecretServiceClient()
        val vault = LinuxSecretServiceVault(client)

        val loaded = vault.load(accountKey)
        assertNull(loaded)
    }

    @Test
    fun testClearRemovesSecret() = runTest {
        val client = FakeSecretServiceClient()
        val vault = LinuxSecretServiceVault(client)

        vault.store(accountKey, CredentialBundle("at_temp", "rt_temp"))
        assertNotNull(vault.load(accountKey))

        vault.clear(accountKey)
        assertNull(vault.load(accountKey))
    }
}
