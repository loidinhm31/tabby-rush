package dev.doggo.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.CredentialBundle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundIsolationTest {

    private lateinit var context: Context
    private lateinit var app: DoggoApplication
    private val testAccountKey = AccountKey("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        app = DoggoApplication.instance
        app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        app.vault.clear(testAccountKey)
        app.accountFiles.clearWipeMarker(testAccountKey)
        app.clearActiveAccount()
    }

    @After
    fun tearDown() = runBlocking {
        app.closeActiveRuntime()
        app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        app.vault.clear(testAccountKey)
        app.accountFiles.clearWipeMarker(testAccountKey)
        app.clearActiveAccount()
    }

    @Test
    fun testWorkerFailsClosedOnPendingWipe() = runBlocking {
        app.accountFiles.writeWipeMarker(testAccountKey, 2L)
        assertTrue(app.accountFiles.hasPendingWipe(testAccountKey))

        val inputData = workDataOf(
            "accountKey" to testAccountKey.value,
            "generation" to 1L
        )

        val worker = TestListenableWorkerBuilder<DoggoSyncWorker>(context)
            .setInputData(inputData)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun testWorkerFailsOnGenerationMismatch() = runBlocking {
        val dummyIdentity = AccountIdentity("user_test", "tester", "test@doggo.dev")
        val matchingKey = AccountKey.create(app.hubConfig.canonicalOrigin, dummyIdentity.userId)
        app.accountFiles.deleteDatabaseAndArtifacts(matchingKey)
        app.accountFiles.writeGeneration(matchingKey, 5L)
        val dummyBundle = CredentialBundle("acc_token", "ref_token")
        app.vault.store(matchingKey, dummyBundle)
        app.authSession.restoreSession(dummyIdentity, matchingKey)

        // Stale generation in worker input: 4L != 5L
        val inputData = workDataOf(
            "accountKey" to matchingKey.value,
            "generation" to 4L
        )

        val worker = TestListenableWorkerBuilder<DoggoSyncWorker>(context)
            .setInputData(inputData)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)

        app.accountFiles.deleteDatabaseAndArtifacts(matchingKey)
        app.vault.clear(matchingKey)
    }

    @Test
    fun testStartupWipeRecoveryCleansPendingWipe() = runBlocking {
        app.accountFiles.writeGeneration(testAccountKey, 1L)
        app.accountFiles.writeWipeMarker(testAccountKey, 2L)
        assertTrue(app.accountFiles.hasPendingWipe(testAccountKey))

        app.completePendingWipesOnStartup()

        assertFalse("Wipe marker should be cleared after startup recovery", app.accountFiles.hasPendingWipe(testAccountKey))
    }

    @Test
    fun testSchedulerCancelAndAwaitQuiescesWork() = runBlocking {
        app.accountFiles.writeGeneration(testAccountKey, 1L)
        app.syncScheduler.triggerImmediate(testAccountKey)

        app.syncScheduler.cancelAndAwait(testAccountKey)

        val infos = androidx.work.WorkManager.getInstance(context)
            .getWorkInfosByTag("doggo_sync_account_${testAccountKey.value}")
            .get()
        assertTrue("All tagged work should be finished or cancelled", infos.all { it.state.isFinished })
    }
}
