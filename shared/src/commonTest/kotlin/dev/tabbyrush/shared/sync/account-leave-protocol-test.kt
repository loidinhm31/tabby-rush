package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.data.EditorDraftEntity
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountLeaveProtocolTest {

    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "tabby-rush",
        appApiKey = "test-key",
        buildMode = BuildMode.DEBUG
    )
    private val accountKey = AccountKey.create(config.canonicalOrigin, "user-123")
    private val identity = AccountIdentity("user-123", "tester", "tester@example.com")

    private suspend fun createTestSetup(): Pair<RoomAccountStore, HubAuthSession> {
        val vault = FakeCredentialVault()
        vault.store(
            accountKey,
            CredentialBundle(
                accessToken = "access",
                refreshToken = "refresh"
            )
        )
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(identity, accountKey)

        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-123",
            database = null,
            appId = "tabby-rush",
            daoOverride = fakeDao
        )
        return Pair(store, session)
    }

    @Test
    fun testLeaveGuardedWhenReviewWorkPending() = runTest {
        val (store, session) = createTestSetup()

        // Insert pending draft
        store.dao.upsertDraft(
            EditorDraftEntity(
                rowId = "row-1",
                draftTitle = "Uncommitted",
                draftCompleted = false,
                localRevision = 1L,
                updatedAt = 1000L,
                isDeleted = false
            )
        )

        var markerWritten = false
        var dbDeleted = false

        val protocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onWriteWipeMarker = { _, _ -> markerWritten = true },
            onClearWipeMarker = { _ -> },
            onDeleteDatabaseArtifacts = { dbDeleted = true; true }
        )

        val success = protocol.executeLeave()
        assertFalse(success) // Blocked by guard!
        assertFalse(markerWritten)
        assertFalse(dbDeleted)
        assertEquals(AuthState.AUTHENTICATED, session.state.value)
    }

    @Test
    fun testLeaveProceedsWhenZeroUnresolvedWork() = runTest {
        val (store, session) = createTestSetup()
        val actions = mutableListOf<String>()

        val protocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onWriteWipeMarker = { _, gen -> actions.add("write_marker:$gen") },
            onClearWipeMarker = { _ -> actions.add("clear_marker") },
            onDeleteDatabaseArtifacts = { actions.add("delete_artifacts"); true },
            schedulerCancelGate = { actions.add("cancel_scheduler") }
        )

        val success = protocol.executeLeave()
        assertTrue(success)

        // Verify exact teardown order:
        // 1. write marker
        // 2. cancel scheduler
        // 3. delete artifacts
        // 4. clear marker
        assertEquals(4, actions.size)
        assertTrue(actions[0].startsWith("write_marker"))
        assertEquals("cancel_scheduler", actions[1])
        assertEquals("delete_artifacts", actions[2])
        assertEquals("clear_marker", actions[3])

        // Vault & session cleared
        assertEquals(AuthState.UNAUTHENTICATED, session.state.value)
    }

    @Test
    fun testDiscardAndLeaveProceedsWithPendingWork() = runTest {
        val (store, session) = createTestSetup()

        // Insert pending outbox conflict
        store.dao.upsertOutbox(
            OutboxEntity(opId = "op-c", tableName = "todos",
            rowId = "row-c",
            kind = "create", title = "Conflicted",
            completed = false,
            createdAt = 1000L, localRevision = 1L,
            deliveryState = OutboxEntity.STATE_CONFLICT,
            createdAtEpochMs = 1000L)
        )

        var markerWritten = false
        var markerCleared = false
        var dbDeleted = false

        val protocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onWriteWipeMarker = { _, _ -> markerWritten = true },
            onClearWipeMarker = { _ -> markerCleared = true },
            onDeleteDatabaseArtifacts = { dbDeleted = true; true }
        )

        val success = protocol.discardAndLeave()
        assertTrue(success)
        assertTrue(markerWritten)
        assertTrue(dbDeleted)
        assertTrue(markerCleared)
        assertEquals(AuthState.UNAUTHENTICATED, session.state.value)
    }

    @Test
    fun testMarkerWriteFailureAbortsTeardown() = runTest {
        val (store, session) = createTestSetup()
        var dbDeleted = false

        val protocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onWriteWipeMarker = { _, _ -> throw IllegalStateException("Disk full") },
            onClearWipeMarker = { _ -> },
            onDeleteDatabaseArtifacts = { dbDeleted = true; true }
        )

        val success = protocol.executeLeave()
        assertFalse(success)
        assertFalse(dbDeleted) // Must NOT delete database if marker fails!
        assertEquals(AuthState.AUTHENTICATED, session.state.value)
    }

    @Test
    fun testDatabaseDeletionFailureKeepsWipeMarkerFailClosed() = runTest {
        val (store, session) = createTestSetup()
        var markerWritten = false
        var markerCleared = false

        val protocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onWriteWipeMarker = { _, _ -> markerWritten = true },
            onClearWipeMarker = { _ -> markerCleared = true },
            onDeleteDatabaseArtifacts = { false } // Deletion failed (e.g. file lock)
        )

        val success = protocol.executeLeave()
        assertFalse(success)
        assertTrue(markerWritten)
        assertFalse(markerCleared) // Fail-closed: marker remains intact for next startup!
    }

    @Test
    fun testSchedulerCancelFailureAbortsTeardownFailClosed() = runTest {
        val (store, session) = createTestSetup()
        var dbDeleted = false
        var markerWritten = false
        var markerCleared = false

        val protocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onWriteWipeMarker = { _, _ -> markerWritten = true },
            onClearWipeMarker = { _ -> markerCleared = true },
            onDeleteDatabaseArtifacts = { dbDeleted = true; true },
            schedulerCancelGate = { throw IllegalStateException("Scheduler timeout") }
        )

        val success = protocol.executeLeave()
        assertFalse(success)
        assertFalse(dbDeleted) // Must NOT delete database if cancel fails!
        assertTrue(store.isFrozen) // Remains fail-closed on cancel failure
        assertTrue(markerWritten)
        assertFalse(markerCleared) // Phase 07: Wipe marker retained when cancel gate fails!
    }
    @Test
    fun testCompletePendingWipeOnStartup() = runTest {
        var dbDeleted = false
        var vaultCleared = false
        var markerCleared = false

        val completed = AccountLeaveProtocol.completePendingWipe(
            accountKey = accountKey,
            hasPendingWipe = { true },
            onDeleteDatabaseArtifacts = { dbDeleted = true; true },
            onClearVault = { vaultCleared = true },
            onClearWipeMarker = { markerCleared = true }
        )

        assertTrue(completed)
        assertTrue(dbDeleted)
        assertTrue(vaultCleared)
        assertTrue(markerCleared)
    }
}
