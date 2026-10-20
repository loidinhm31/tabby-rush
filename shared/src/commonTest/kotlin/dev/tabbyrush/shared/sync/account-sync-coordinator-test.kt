package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.*
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.RoomAccountStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Network orchestration + transactions live in AdversarialSyncSqliteTest; this checks admission without I/O. */
class AccountSyncCoordinatorTest {
    @Test fun switchedAccountAndFrozenStoreCannotStartNetwork() = runTest {
        val config = HubConfig("http://localhost:3100", "tabby-rush", "test-key", BuildMode.DEBUG)
        var calls = 0
        val session = HubAuthSession(config, FakeCredentialVault(), TestHttpEngine { calls++; HttpStatusCode.OK to "{}" })
        val store = RoomAccountStore(AccountKey.create(config.canonicalOrigin, "owner"), "owner", daoOverride = FakeTabbyRushDao())
        val coordinator = AccountSyncCoordinator(store, session)
        coordinator.triggerSync()
        assertEquals(0, calls)
        assertEquals("ACCOUNT_NOT_READY", coordinator.progress.value.safeError?.code)
        store.isFrozen = true
        coordinator.triggerSync()
        assertEquals(0, calls)
    }
}
