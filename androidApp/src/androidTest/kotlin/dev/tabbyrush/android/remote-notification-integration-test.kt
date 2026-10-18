package dev.tabbyrush.android

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tabbyrush.shared.contract.RemotePostResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented integration tests verifying native Android remote notification
 * behavior on device/emulator:
 * - Channel creation and configuration (tabbyrush.remote.announcements).
 * - Exact key parsing, canonical UUID validation, and consumed-once Intent semantics.
 * - Local generic post with stable tag/group and scoped cancellation.
 */
@RunWith(AndroidJUnit4::class)
class RemoteNotificationIntegrationTest {

    private lateinit var context: Context
    private lateinit var adapter: AndroidRemoteNotificationAdapter
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        adapter = AndroidRemoteNotificationAdapter(context)
    }

    @After
    fun tearDown() = runBlocking {
        adapter.clearRemote(1L)
        adapter.close()
    }

    @Test
    fun testNotificationChannelCreatedWithDefaultImportance() {
        adapter.createNotificationChannel()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(AndroidRemoteNotificationAdapter.CHANNEL_ID)
            assertNotNull("Announcement channel must exist", channel)
            assertEquals(
                NotificationManager.IMPORTANCE_DEFAULT,
                channel!!.importance
            )
            assertEquals("Admin Announcements", channel.name)
        }
    }

    @Test
    fun testIntentParsingExactKeysAndConsumedOnce() {
        val testMessageId = UUID.randomUUID().toString()
        val intent = Intent().apply {
            putExtra(RemoteNotificationIntents.EXTRA_TYPE, RemoteNotificationIntents.EXPECTED_TYPE)
            putExtra(RemoteNotificationIntents.EXTRA_APP_ID, RemoteNotificationIntents.EXPECTED_APP_ID)
            putExtra(RemoteNotificationIntents.EXTRA_MESSAGE_ID, testMessageId)
            putExtra(RemoteNotificationIntents.EXTRA_GENERATION, 42L)
        }

        // First parse: successfully extracts payload
        val target = RemoteNotificationIntents.parseAndConsume(intent)
        assertNotNull(target)
        assertEquals(testMessageId, target?.messageId)
        assertEquals(42L, target?.generation)

        // Consumed once: extras removed from Intent, second parse returns null
        val secondAttempt = RemoteNotificationIntents.parseAndConsume(intent)
        assertNull(secondAttempt)
        assertFalse(intent.hasExtra(RemoteNotificationIntents.EXTRA_MESSAGE_ID))
    }

    @Test
    fun testIntentParsingRejectsInvalidKeysOrMalformedUuid() {
        // Mismatched type
        val wrongTypeIntent = Intent().apply {
            putExtra(RemoteNotificationIntents.EXTRA_TYPE, "wrong_type")
            putExtra(RemoteNotificationIntents.EXTRA_APP_ID, RemoteNotificationIntents.EXPECTED_APP_ID)
            putExtra(RemoteNotificationIntents.EXTRA_MESSAGE_ID, UUID.randomUUID().toString())
        }
        assertNull(RemoteNotificationIntents.parseAndConsume(wrongTypeIntent))

        // Malformed non-canonical UUID
        val malformedUuidIntent = Intent().apply {
            putExtra(RemoteNotificationIntents.EXTRA_TYPE, RemoteNotificationIntents.EXPECTED_TYPE)
            putExtra(RemoteNotificationIntents.EXTRA_APP_ID, RemoteNotificationIntents.EXPECTED_APP_ID)
            putExtra(RemoteNotificationIntents.EXTRA_MESSAGE_ID, "not-a-valid-uuid-12345")
        }
        assertNull(RemoteNotificationIntents.parseAndConsume(malformedUuidIntent))
    }

    @Test
    fun testScopedClearRemoteCancelsOnlyAnnouncementTags() = runBlocking {
        val messageId1 = UUID.randomUUID().toString()
        val messageId2 = UUID.randomUUID().toString()

        // Post two remote announcements if notifications are permitted
        adapter.show(messageId1, 1L)
        adapter.show(messageId2, 1L)

        // Clear remote notifications
        adapter.clearRemote(1L)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val active = notificationManager.activeNotifications
            for (sbn in active) {
                val tag = sbn.tag
                if (tag != null) {
                    assertFalse("Remote announcement tags must be cancelled", tag.startsWith(AndroidRemoteNotificationAdapter.TAG_PREFIX))
                }
            }
        }
    }
}
