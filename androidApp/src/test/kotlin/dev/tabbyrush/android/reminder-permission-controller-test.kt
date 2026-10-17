package dev.tabbyrush.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPermissionControllerTest {

    @Test
    fun testPermissionConstants() {
        assertEquals(
            "android.permission.POST_NOTIFICATIONS",
            ReminderPermissionController.PERMISSION_POST_NOTIFICATIONS
        )
    }

    @Test
    fun testPermissionStateTypes() {
        val exact = ReminderPermissionState.Exact
        val bestEffort = ReminderPermissionState.BestEffort
        val denied = ReminderPermissionState.NotificationDenied
        val channelDisabled = ReminderPermissionState.ChannelDisabled

        assertTrue(exact is ReminderPermissionState)
        assertTrue(bestEffort is ReminderPermissionState)
        assertTrue(denied is ReminderPermissionState)
        assertTrue(channelDisabled is ReminderPermissionState)
    }
}
