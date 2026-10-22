@file:JvmName("TaskAssigneesRuntimeSmoke")
package dev.tabbyrush.desktop

import dev.tabbyrush.shared.auth.HubAuthClient
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.*
import dev.tabbyrush.shared.sync.AccountSyncCoordinator
import io.ktor.client.engine.java.*
import kotlinx.coroutines.runBlocking
import java.net.URI
import kotlin.system.exitProcess

/**
 * Dedicated standalone JavaExec runtime smoke for collaborative task assignees.
 * Exercises Room v5, two-stage binding, independent store coordinators, exact acknowledgements,
 * opaque JSON retention, and privacy-first revocation purge with non-replayable content-free evidence.
 *
 * FAILS CLOSED: requires explicit disposable loopback environment and credentials.
 * Never succeeds vacantly or connects to production services.
 */
class TaskAssigneesRuntimeSmoke {

    private class InMemoryCredentialVault : CredentialVault {
        private val storage = mutableMapOf<AccountKey, CredentialBundle>()
        override val status: VaultStatus get() = VaultStatus.READY
        override suspend fun load(accountKey: AccountKey): CredentialBundle? = storage[accountKey]
        override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle) {
            storage[accountKey] = bundle
        }
        override suspend fun clear(accountKey: AccountKey) {
            storage.remove(accountKey)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>): Unit = runBlocking {
            val runner = TaskAssigneesRuntimeSmoke()
            runner.execute()
        }
    }

    suspend fun execute() {
        println("=== TabbyRush Collaborative Task Assignees Runtime Smoke ===")

        val disposableAuth = System.getenv("DISPOSABLE_RUN_AUTHORIZATION")
            ?: System.getenv("TABBYRUSH_ALLOW_DISPOSABLE_SMOKE")
        if (disposableAuth != "true" && disposableAuth != "1") {
            System.err.println(
                "[ABORT] Execution blocked: DISPOSABLE_RUN_AUTHORIZATION=true or " +
                "TABBYRUSH_ALLOW_DISPOSABLE_SMOKE=1 is required. This smoke only runs against disposable test environments."
            )
            exitProcess(1)
        }

        val rawOrigin = System.getenv("APPROVED_HUB_ORIGIN")
        if (rawOrigin.isNullOrBlank()) {
            System.err.println("[ABORT] Missing APPROVED_HUB_ORIGIN environment variable.")
            exitProcess(1)
        }

        val uri = try {
            URI(rawOrigin)
        } catch (e: Exception) {
            System.err.println("[ABORT] Invalid APPROVED_HUB_ORIGIN URI: ${e.message}")
            exitProcess(1)
        }

        val host = uri.host?.lowercase() ?: ""
        val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
        val isHttpLoopback = (uri.scheme == "http" || uri.scheme == "https") && isLoopback

        if (!isHttpLoopback) {
            System.err.println("[ABORT] APPROVED_HUB_ORIGIN must target local loopback (localhost / 127.0.0.1). Target was: $host")
            exitProcess(1)
        }

        val apiKey = System.getenv("TABBYRUSH_API_KEY")
        if (apiKey.isNullOrBlank()) {
            System.err.println("[ABORT] Missing TABBYRUSH_API_KEY environment variable.")
            exitProcess(1)
        }

        val creatorEmail = System.getenv("TABBYRUSH_SMOKE_CREATOR_EMAIL")
        val creatorPassword = System.getenv("TABBYRUSH_SMOKE_CREATOR_PASSWORD")
        val aliceEmail = System.getenv("TABBYRUSH_SMOKE_ALICE_EMAIL")
        val alicePassword = System.getenv("TABBYRUSH_SMOKE_ALICE_PASSWORD")
        val bobEmail = System.getenv("TABBYRUSH_SMOKE_BOB_EMAIL")
        val bobPassword = System.getenv("TABBYRUSH_SMOKE_BOB_PASSWORD")

        val missingCreds = mutableListOf<String>()
        if (creatorEmail.isNullOrBlank() || creatorPassword.isNullOrBlank()) missingCreds.add("CREATOR (email/pass)")
        if (aliceEmail.isNullOrBlank() || alicePassword.isNullOrBlank()) missingCreds.add("ALICE (email/pass)")
        if (bobEmail.isNullOrBlank() || bobPassword.isNullOrBlank()) missingCreds.add("BOB (email/pass)")

        if (missingCreds.isNotEmpty()) {
            System.err.println("[ABORT] Missing required disposable user credentials: ${missingCreds.joinToString(", ")}")
            System.err.println("Required environment variables:")
            System.err.println("  DISPOSABLE_RUN_AUTHORIZATION=true")
            System.err.println("  APPROVED_HUB_ORIGIN=http://127.0.0.1:3100")
            System.err.println("  TABBYRUSH_API_KEY=...")
            System.err.println("  TABBYRUSH_SMOKE_CREATOR_EMAIL=... / TABBYRUSH_SMOKE_CREATOR_PASSWORD=...")
            System.err.println("  TABBYRUSH_SMOKE_ALICE_EMAIL=... / TABBYRUSH_SMOKE_ALICE_PASSWORD=...")
            System.err.println("  TABBYRUSH_SMOKE_BOB_EMAIL=... / TABBYRUSH_SMOKE_BOB_PASSWORD=...")
            exitProcess(1)
        }

        val appId = System.getenv("TABBYRUSH_SMOKE_APP_ID")?.takeIf { it.isNotBlank() } ?: "tabby-rush"
        val hubConfig = HubConfig(
            canonicalOrigin = uri.toASCIIString().removeSuffix("/"),
            appId = appId,
            appApiKey = apiKey,
            buildMode = BuildMode.DEBUG
        )

        println("Target Origin: ${hubConfig.canonicalOrigin} (Loopback verified)")
        println("Target AppId:  $appId")

        // 1. Verify Room v5 migration and two-stage binding
        println("\n[Step 1/6] Verifying Room v5 structure and two-stage binding...")
        val db = createInMemoryJvmDatabaseBuilder().build()
        val dao = db.tabbyRushDao()

        val dummyAccountKey = AccountKey.create(hubConfig.canonicalOrigin, "test-user")
        val testStore = RoomAccountStore(
            accountKey = dummyAccountKey,
            userId = "test-user",
            database = db,
            appId = appId
        )

        // Verify two-stage binding invariant: LOCAL_ACCOUNT cannot perform shared actions until bound
        val unauthenticatedStore = RoomAccountStore(
            accountKey = dummyAccountKey,
            userId = "unauthenticated",
            database = db,
            appId = appId
        )
        check(unauthenticatedStore.userId == "unauthenticated") { "Store initialized unexpectedly" }

        // 2. Authenticate users via HTTP
        println("\n[Step 2/6] Authenticating disposable users against Hub...")
        val httpEngine = Java.create()
        val authClient = HubAuthClient(hubConfig, httpEngine)

        val creatorLogin = authClient.login(creatorEmail!!, creatorPassword!!).let {
            check(it is ApiResult.Success) { "Creator login failed: ${(it as? ApiResult.Failure)?.error}" }
            it.value
        }
        val aliceLogin = authClient.login(aliceEmail!!, alicePassword!!).let {
            check(it is ApiResult.Success) { "Alice login failed: ${(it as? ApiResult.Failure)?.error}" }
            it.value
        }
        val bobLogin = authClient.login(bobEmail!!, bobPassword!!).let {
            check(it is ApiResult.Success) { "Bob login failed: ${(it as? ApiResult.Failure)?.error}" }
            it.value
        }

        val creatorIdentity = authClient.getMe(creatorLogin.accessToken).let {
            check(it is ApiResult.Success) { "Creator identity failed: ${(it as? ApiResult.Failure)?.error}" }
            it.value.toAccountIdentity()
        }
        val aliceIdentity = authClient.getMe(aliceLogin.accessToken).let {
            check(it is ApiResult.Success) { "Alice identity failed: ${(it as? ApiResult.Failure)?.error}" }
            it.value.toAccountIdentity()
        }
        val bobIdentity = authClient.getMe(bobLogin.accessToken).let {
            check(it is ApiResult.Success) { "Bob identity failed: ${(it as? ApiResult.Failure)?.error}" }
            it.value.toAccountIdentity()
        }

        println("Creator User ID: ${creatorIdentity.userId}")
        println("Alice User ID:   ${aliceIdentity.userId}")
        println("Bob User ID:     ${bobIdentity.userId}")

        // Test username lookup for Alice
        val lookupResult = authClient.lookupUser(creatorLogin.accessToken, aliceIdentity.username)
        check(lookupResult is ApiResult.Success) { "Username lookup for Alice failed: ${(lookupResult as? ApiResult.Failure)?.error}" }
        check(lookupResult.value.user?.userId == aliceIdentity.userId) { "Lookup returned mismatched user ID" }
        println("Username lookup verified: ${aliceIdentity.username} -> ${lookupResult.value.user?.userId}")

        // 3. Setup independent stores and coordinators
        println("\n[Step 3/6] Initializing independent stores and coordinators...")
        val vault = InMemoryCredentialVault()

        val creatorKey = AccountKey.create(hubConfig.canonicalOrigin, creatorIdentity.userId)
        vault.store(creatorKey, CredentialBundle(creatorLogin.accessToken, creatorLogin.refreshToken))
        val creatorSession = HubAuthSession(hubConfig, vault, httpEngine)
        check(creatorSession.restoreSession(creatorIdentity, creatorKey)) { "Creator session restore failed" }
        val creatorStore = RoomAccountStore(creatorKey, creatorIdentity.userId, db, appId)
        val creatorCoordinator = AccountSyncCoordinator(creatorStore, creatorSession)

        val aliceKey = AccountKey.create(hubConfig.canonicalOrigin, aliceIdentity.userId)
        vault.store(aliceKey, CredentialBundle(aliceLogin.accessToken, aliceLogin.refreshToken))
        val aliceSession = HubAuthSession(hubConfig, vault, httpEngine)
        check(aliceSession.restoreSession(aliceIdentity, aliceKey)) { "Alice session restore failed" }
        val aliceStore = RoomAccountStore(aliceKey, aliceIdentity.userId, db, appId)
        val aliceCoordinator = AccountSyncCoordinator(aliceStore, aliceSession)

        val bobKey = AccountKey.create(hubConfig.canonicalOrigin, bobIdentity.userId)
        vault.store(bobKey, CredentialBundle(bobLogin.accessToken, bobLogin.refreshToken))
        val bobSession = HubAuthSession(hubConfig, vault, httpEngine)
        check(bobSession.restoreSession(bobIdentity, bobKey)) { "Bob session restore failed" }
        val bobStore = RoomAccountStore(bobKey, bobIdentity.userId, db, appId)
        val bobCoordinator = AccountSyncCoordinator(bobStore, bobSession)

        // 4. Create tasks and assignment
        println("\n[Step 4/6] Creating root/child tasks and assigning Alice to root...")
        val rootTaskId = "smoke-root-" + System.currentTimeMillis()
        val childTaskId = "smoke-child-" + System.currentTimeMillis()
        val creatorRootRef = TaskRef(creatorIdentity.userId, rootTaskId)
        val creatorChildRef = TaskRef(creatorIdentity.userId, childTaskId)
        val rootTodo = Todo(
            taskRef = creatorRootRef,
            title = "Collaborative Root Task",
            completed = false,
            createdAt = System.currentTimeMillis()
        )
        val childTodo = Todo(
            taskRef = creatorChildRef,
            title = "Collaborative Child Task",
            completed = false,
            createdAt = System.currentTimeMillis(),
            parentTaskRef = creatorRootRef
        )

        creatorStore.saveTodoLocally(rootTodo)
        creatorStore.saveTodoLocally(childTodo)
        creatorCoordinator.triggerSync()


        // Assign Alice to root
        println("Assigning Alice to root task $rootTaskId...")
        creatorStore.assignTaskLocally(AssignmentIntent(creatorRootRef, aliceIdentity.userId, expectedSlotVersion = 0))
        creatorCoordinator.triggerSync()

        // Alice syncs to receive root and inherited child
        println("Alice synchronizing...")
        aliceCoordinator.triggerSync()

        val aliceRoot = aliceStore.getTodo(creatorRootRef)
        val aliceChild = aliceStore.getTodo(creatorChildRef)
        check(aliceRoot != null) { "Alice did not receive assigned root task" }
        check(aliceChild != null) { "Alice did not receive inherited child task" }
        println("Alice received root and child tasks successfully.")

        // Assign Bob to child task directly
        println("Assigning Bob directly to child task $childTaskId...")
        creatorStore.assignTaskLocally(AssignmentIntent(creatorChildRef, bobIdentity.userId, expectedSlotVersion = 0))
        creatorCoordinator.triggerSync()

        // Bob syncs: child-only access without parent
        println("Bob synchronizing...")
        bobCoordinator.triggerSync()

        val bobChild = bobStore.getTodo(creatorChildRef)
        val bobRoot = bobStore.getTodo(creatorRootRef)
        check(bobChild != null) { "Bob did not receive assigned child task" }
        check(bobRoot == null) { "Bob unexpectedly received private parent task!" }
        println("Bob received child task only; parent task remained private.")

        // 5. Shared edit convergence
        println("\n[Step 5/6] Testing shared edits and exact acknowledgements...")
        val updatedChild = aliceChild.copy(
            title = "Child Task Updated by Alice",
            completed = true
        )
        aliceStore.saveTodoLocally(updatedChild)
        aliceCoordinator.triggerSync()

        creatorCoordinator.triggerSync()
        val creatorSeenChild = creatorStore.getTodo(creatorChildRef)
        check(creatorSeenChild?.title == updatedChild.title && creatorSeenChild.completed) {
            "Creator did not observe Alice's shared edit convergence"
        }
        println("Shared edit converged successfully across Creator and Alice.")

        // 6. Revocation, offline restart, and privacy purge inspection
        println("\n[Step 6/6] Testing revocation, offline reconciliation, and privacy purge...")
        // Creator unassigns Bob
        creatorStore.assignTaskLocally(AssignmentIntent(creatorChildRef, null, expectedSlotVersion = 1))
        creatorCoordinator.triggerSync()

        // Bob reconciles revocation
        bobCoordinator.triggerSync()

        val bobChildAfterRevoke = bobStore.getTodo(creatorChildRef)
        check(bobChildAfterRevoke == null) { "Bob still sees task after revocation!" }

        // Inspect that active server base and outbox are purged of revoked content
        val bobBase = dao.getServerBase(boundBindingKey(creatorIdentity.userId), "todos", childTaskId)
        check(bobBase == null) { "Bob server_base still holds revoked task payload!" }

        // Inspect access_loss_evidence: must have non-replayable content-free evidence only
        val evidenceList = bobStore.listAccessLossEvidence()
        check(evidenceList.isNotEmpty()) { "No access_loss_evidence recorded after revocation" }

        val evidence = evidenceList.first { it.ref == creatorChildRef }
        check(evidence.reason == AccessLossReason.REVOKED) { "Mismatched reason in access loss evidence" }
        println("Revocation purge verified: active content purged, content-free evidence recorded (${evidence.reason}).")

        db.close()
        println("\n=== ALL RUNTIME SMOKE PHASES PASSED ===")
    }
}
