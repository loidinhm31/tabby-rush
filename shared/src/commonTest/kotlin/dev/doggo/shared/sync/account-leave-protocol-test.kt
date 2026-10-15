package dev.doggo.shared.sync

import dev.doggo.shared.auth.FakeCredentialVault
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.auth.TestHttpEngine
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.data.EditorDraftEntity
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountLeaveProtocolTest {

    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "doggo",
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

        val fakeDao = FakeDoggoDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-123",
            database = null,
            appId = "doggo",
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
            OutboxEntity(
                opId = "op-c",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-c",
                kind = "create",
                expectedRevision = 0L,
                title = "Conflicted",
                completed = false,
                createdAt = 1000L,
                digest = "d-c",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L
            )
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
