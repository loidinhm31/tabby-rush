package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.sync.AccountLeaveProtocol
import dev.tabbyrush.shared.sync.DeliveryGateState
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderTeardownBarrierSqliteTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private lateinit var store: RoomAccountStore
    private lateinit var database: TabbyRushDatabase
    private lateinit var gate: ReminderDeliveryGate
    private lateinit var session: HubAuthSession

    private val accountKey = AccountKey.create("http://localhost:3000", "user-barrier-test")
    private var currentGeneration = 1L
    private var wipeMarkerPresent = false

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-teardown-barrier-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()

        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-barrier-test",
            database = database,
            appId = "tabby-rush"
        )

        gate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { currentGeneration },
            hasPendingWipeMarker = { wipeMarkerPresent }
        )
        store.deliveryGate = gate
        val vault = FakeCredentialVault()
        val config = HubConfig("http://localhost:3000", "tabbyrush", "test-key", BuildMode.DEBUG)
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        session = HubAuthSession(config, vault, engine)
        kotlinx.coroutines.runBlocking {
            vault.store(accountKey, CredentialBundle("access", "refresh"))
            session.restoreSession(AccountIdentity("user-barrier-test", "tester", "t@example.com"), accountKey)
        }
    }

    @AfterTest
    fun tearDown() {
        store.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testTeardownAwaitsInFlightDeliveryBeforeClosingRoom() = runTest {
        val deliveryEntered = CompletableDeferred<Unit>()
        val deliveryCanFinish = CompletableDeferred<Unit>()
        var deliveryFinished = false
        var roomClosedDuringDelivery: Boolean? = null

        val deliveryJob = async {
            gate.withDeliveryAdmission(expectedGeneration = 1L) {
                deliveryEntered.complete(Unit)
                deliveryCanFinish.await()

                // Check that SQLite is still operable and Room has not been closed yet!
                roomClosedDuringDelivery = try {
                    store.dao.countServerBase()
                    false
                } catch (_: Exception) {
                    true
                }
                deliveryFinished = true
            }
        }

        // Wait until delivery has acquired admission and is in-flight
        deliveryEntered.await()

        var markerWritten = false
        var dbDeleted = false

        val leaveProtocol = AccountLeaveProtocol(
            store = store,
            session = session,
            onReadGeneration = { currentGeneration },
            onWriteGeneration = { _, next -> currentGeneration = next },
            onWriteWipeMarker = { _, _ -> markerWritten = true },
            onClearWipeMarker = { _ -> },
            onDeleteDatabaseArtifacts = { dbDeleted = true; true }
        )

        val teardownJob = async {
            leaveProtocol.executeLeave()
        }

        // Allow teardown to progress past review check and enter closeAndDrain()
        while (gate.getState() != DeliveryGateState.CLOSING) {
            yield()
        }

        // Verify gate transitioned to CLOSING while delivery is in flight
        assertEquals(DeliveryGateState.CLOSING, gate.getState())
        assertFalse(deliveryFinished)
        assertFalse(markerWritten) // Marker and deletion have NOT run yet!

        // Now allow delivery callback to complete its work
        deliveryCanFinish.complete(Unit)
        deliveryJob.await()

        assertTrue(deliveryFinished)
        assertEquals(false, roomClosedDuringDelivery) // Room was NOT closed during delivery!

        // Teardown should now complete cleanly
        val teardownSuccess = teardownJob.await()
        assertTrue(teardownSuccess)
        assertTrue(markerWritten)
        assertTrue(dbDeleted)
        assertEquals(DeliveryGateState.CLOSED, gate.getState())
    }
}
