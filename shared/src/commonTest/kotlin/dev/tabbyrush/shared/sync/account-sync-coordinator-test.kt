package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.*
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.boundBindingKey
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AccountSyncCoordinatorTest {
    private val config = HubConfig("http://localhost:3100", "tabby-rush", "test-key", BuildMode.DEBUG)

    @Test fun switchedAccountAndFrozenStoreCannotStartNetwork() = runTest {
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

    @Test fun discardConflictDiscardsOpForTaskRef() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val key = AccountKey.create(config.canonicalOrigin, "user-alice")
        val store = RoomAccountStore(key, "user-alice", daoOverride = fakeDao)
        val vault = FakeCredentialVault()
        vault.store(key, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, TestHttpEngine { HttpStatusCode.OK to "{}" })
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), key)

        val coordinator = AccountSyncCoordinator(store, session)
        val taskRef = TaskRef("user-alice", "task-1")

        // Populate a conflict outbox entry
        val boundKey = boundBindingKey("user-alice")
        fakeDao.upsertOutbox(
            OutboxEntity(
                bindingKey = boundKey,
                ownerUserId = "user-alice",
                tableName = "todos",
                rowId = "task-1",
                opId = "op-1",
                kind = "update",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                title = "Conflict task",
                completed = false,
                createdAt = 100L,
                createdAtEpochMs = 100L,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderCount = 1L
            )
        )

        coordinator.discardConflict(taskRef)

        val entry = fakeDao.getOutboxEntry("op-1")
        assertNotNull(entry)
        assertEquals("DISCARDED", entry.deliveryState)
        assertEquals("EXPLICIT_DISCARD", entry.resolution)
    }

    @Test fun copyAsNewCreatesFreshRootTaskForTaskRef() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val key = AccountKey.create(config.canonicalOrigin, "user-alice")
        val store = RoomAccountStore(key, "user-alice", daoOverride = fakeDao)
        val vault = FakeCredentialVault()
        vault.store(key, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, TestHttpEngine { HttpStatusCode.OK to "{}" })
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), key)

        val coordinator = AccountSyncCoordinator(store, session)
        val taskRef = TaskRef("user-bob", "task-shared-1")

        // Populate conflict outbox entry
        val boundKey = boundBindingKey("user-bob")
        fakeDao.upsertOutbox(
            OutboxEntity(
                bindingKey = boundKey,
                ownerUserId = "user-bob",
                tableName = "todos",
                rowId = "task-shared-1",
                opId = "op-bob-1",
                kind = "update",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                title = "Copied shared task",
                completed = false,
                createdAt = 100L,
                createdAtEpochMs = 100L,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderCount = 1L
            )
        )

        coordinator.copyAsNew(taskRef)

        // Old conflict op is discarded
        val oldOp = fakeDao.getOutboxEntry("op-bob-1")
        assertNotNull(oldOp)
        assertEquals("DISCARDED", oldOp.deliveryState)

        // Fresh task created under user-alice
        val todos = store.loadTodos()
        assertEquals(1, todos.size)
        assertEquals("user-alice", todos[0].ownerUserId)
        assertEquals("Copied shared task", todos[0].title)
        assertNull(todos[0].parentTaskRef)
    }
}
