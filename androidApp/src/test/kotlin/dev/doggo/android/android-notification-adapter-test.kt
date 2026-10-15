package dev.doggo.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNotificationAdapterTest {

    @Test
    fun testAdapterConstants() {
        assertEquals("doggo_reminders", AndroidNotificationAdapter.CHANNEL_ID)
        assertEquals("dev.doggo.android.ACTION_DELIVER_REMINDER", AndroidNotificationAdapter.ACTION_DELIVER_REMINDER)
        assertEquals("extra_task_id", AndroidNotificationAdapter.EXTRA_TASK_ID)
        assertEquals("extra_schedule_token", AndroidNotificationAdapter.EXTRA_SCHEDULE_TOKEN)
        assertEquals("extra_occurrence_index", AndroidNotificationAdapter.EXTRA_OCCURRENCE_INDEX)
        assertEquals("extra_notification_id", AndroidNotificationAdapter.EXTRA_NOTIFICATION_ID)
        assertEquals("extra_title", AndroidNotificationAdapter.EXTRA_TITLE)
    }

    @Test
    fun testCapabilitiesModel() {
        val fullyEnabled = AndroidReminderCapabilities(
            canPostNotifications = true,
            canScheduleExactAlarms = true,
            isChannelEnabled = true
        )
        assertTrue(fullyEnabled.canPostNotifications)
        assertTrue(fullyEnabled.canScheduleExactAlarms)
        assertTrue(fullyEnabled.isChannelEnabled)

        val timingDegraded = AndroidReminderCapabilities(
            canPostNotifications = true,
            canScheduleExactAlarms = false,
            isChannelEnabled = true
        )
        assertTrue(timingDegraded.canPostNotifications)
        assertFalse(timingDegraded.canScheduleExactAlarms)

        val notificationsBlocked = AndroidReminderCapabilities(
            canPostNotifications = false,
            canScheduleExactAlarms = true,
            isChannelEnabled = false
        )
        assertFalse(notificationsBlocked.canPostNotifications)
        assertFalse(notificationsBlocked.isChannelEnabled)
    }
}
