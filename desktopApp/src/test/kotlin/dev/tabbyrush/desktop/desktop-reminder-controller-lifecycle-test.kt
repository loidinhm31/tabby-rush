package dev.tabbyrush.desktop

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.contract.VaultStatus
import dev.tabbyrush.shared.data.JvmAccountFiles
import dev.tabbyrush.shared.sync.DeliveryGateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopReminderControllerLifecycleTest {

    private lateinit var tempDir: File
    private val accountKey = AccountKey("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd")

    private class RecordingReminderAdapter : DesktopReminderAdapter {
        val cancelledAccounts = mutableListOf<AccountKey>()
        private var listener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)? = null

        override fun setScheduleListener(listener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)?) {
            this.listener = listener
        }

        override suspend fun queryCapabilities(): ReminderCapabilities = ReminderCapabilities(true, true, true)
        override suspend fun reconcileSchedule(accountKey: AccountKey, generation: Long, desiredAlarms: List<ScheduledAlarmHandle>) {
            listener?.invoke(accountKey, generation, desiredAlarms)
        }
        override suspend fun postOwnedNotification(accountKey: AccountKey, generation: Long, taskId: String, scheduleToken: String, occurrenceIndex: Int, title: String, dueEpochMs: Long): String = "p-1"
        override suspend fun removeOwnedNotification(accountKey: AccountKey, platformNotificationId: String) {}
        override suspend fun cancelAllForAccount(accountKey: AccountKey) {
            cancelledAccounts.add(accountKey)
        }
    }
    private class InMemoryVault : CredentialVault {
        val clearedKeys = mutableListOf<AccountKey>()
        override val status: VaultStatus = VaultStatus.READY
        override suspend fun store(accountKey: AccountKey, bundle: dev.tabbyrush.shared.contract.CredentialBundle) {}
        override suspend fun load(accountKey: AccountKey): dev.tabbyrush.shared.contract.CredentialBundle? = null
        override suspend fun clear(accountKey: AccountKey) {
            clearedKeys.add(accountKey)
        }
    }

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("tabbyrush-reminder-lifecycle-test").toFile()
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testStartupWipeRecoveryCancelsPlatformNotificationsBeforeClearingMarker() = runTest {
        val accountFiles = JvmAccountFiles(baseDir = tempDir)
        val vault = InMemoryVault()
        val adapter = RecordingReminderAdapter()

        // Create an account with a pending wipe marker
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        dbFile.parentFile.mkdirs()
        dbFile.writeText("dummy sqlite database")
        accountFiles.writeWipeMarker(accountKey, 1L)
        assertTrue(accountFiles.hasPendingWipe(accountKey))

        // Simulate startup wipe recovery
        accountFiles.baseDir.listFiles()?.filter { it.isDirectory && it.name.length == 64 }?.forEach { dir ->
            val key = AccountKey(dir.name)
            if (accountFiles.hasPendingWipe(key)) {
                adapter.cancelAllForAccount(key)
                if (accountFiles.deleteDatabaseAndArtifacts(key)) {
                    vault.clear(key)
                    accountFiles.clearWipeMarker(key)
                }
            }
        }

        // Platform notifications must be cancelled
        assertEquals(listOf(accountKey), adapter.cancelledAccounts)
        // Vault cleared
        assertEquals(listOf(accountKey), vault.clearedKeys)
        // Wipe marker cleared
        assertFalse(accountFiles.hasPendingWipe(accountKey))
        // Database removed
        assertFalse(dbFile.exists())
    }

    @Test
    fun testStartupWipeRecoveryRetainsMarkerWhenReminderCancellationFails() = runTest {
        val accountFiles = JvmAccountFiles(baseDir = tempDir)
        val vault = InMemoryVault()
        val failingAdapter = object : DesktopReminderAdapter {
            override fun setScheduleListener(listener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)?) {}
            override suspend fun queryCapabilities(): ReminderCapabilities = ReminderCapabilities(true, true, true)
            override suspend fun reconcileSchedule(accountKey: AccountKey, generation: Long, desiredAlarms: List<ScheduledAlarmHandle>) {}
            override suspend fun postOwnedNotification(accountKey: AccountKey, generation: Long, taskId: String, scheduleToken: String, occurrenceIndex: Int, title: String, dueEpochMs: Long): String = "p-1"
            override suspend fun removeOwnedNotification(accountKey: AccountKey, platformNotificationId: String) {}
            override suspend fun cancelAllForAccount(accountKey: AccountKey) {
                throw RuntimeException("Simulated platform notification cancellation failure")
            }
        }

        // Create an account with a pending wipe marker and database file
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        dbFile.parentFile.mkdirs()
        dbFile.writeText("dummy sqlite database")
        accountFiles.writeWipeMarker(accountKey, 1L)
        assertTrue(accountFiles.hasPendingWipe(accountKey))

        // Simulate startup wipe recovery with fail-closed semantics
        accountFiles.baseDir.listFiles()?.filter { it.isDirectory && it.name.length == 64 }?.forEach { dir ->
            val key = AccountKey(dir.name)
            if (accountFiles.hasPendingWipe(key)) {
                try {
                    failingAdapter.cancelAllForAccount(key)
                } catch (_: Exception) {
                    return@forEach // Fail-closed: retain wipe marker if reminder cancellation fails
                }
                if (accountFiles.deleteDatabaseAndArtifacts(key)) {
                    vault.clear(key)
                    accountFiles.clearWipeMarker(key)
                }
            }
        }

        // Fail-closed assertions:
        // 1. Wipe marker MUST still be pending
        assertTrue(accountFiles.hasPendingWipe(accountKey), "Wipe marker must be retained when cancellation fails")
        // 2. Database file MUST NOT be deleted
        assertTrue(dbFile.exists(), "Database file must not be deleted when cancellation fails")
        // 3. Vault MUST NOT be cleared
        assertTrue(vault.clearedKeys.isEmpty(), "Vault must not be cleared when cancellation fails")
    }

    @Test
    fun testRuntimeInitializationSetsUpReminderSubsystem() = runTest {
        val accountFiles = JvmAccountFiles(baseDir = tempDir)
        val lockFile = File(tempDir, "tabbyrush.lock")
        val instanceGuard = SingleInstanceGuard.tryAcquire(lockFile)!!
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val controller = DesktopController(BuildMode.DEBUG, scope, instanceGuard)

            val runtime = controller.getOrCreateAccountRuntime(accountKey)

            assertNotNull(runtime.reminderCoordinator)
            assertNotNull(runtime.deliveryGate)
            assertNotNull(runtime.reminderScheduler)
            assertNotNull(runtime.scheduleObserverJob)
            assertEquals(DeliveryGateState.OPEN, runtime.deliveryGate.getState())

            controller.disposeSuspend()
            assertEquals(DeliveryGateState.CLOSED, runtime.deliveryGate.getState())
        } finally {
            scope.cancel()
            instanceGuard.close()
        }
    }
}
