package dev.tabbyrush.android

import android.content.Intent
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.TaskRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidNotificationAdapterTest {

    @Test
    fun testIntentFactoryConstants() {
        assertEquals("tabbyrush_reminders", AndroidNotificationIntentFactory.CHANNEL_ID)
        assertEquals("dev.tabbyrush.android.REMINDERS", AndroidNotificationIntentFactory.GROUP_KEY)
        assertEquals("dev.tabbyrush.android.ACTION_DELIVER_REMINDER", AndroidNotificationIntentFactory.ACTION_DELIVER_REMINDER)
        assertEquals("dev.tabbyrush.android.ACTION_VIEW_TASK", AndroidNotificationIntentFactory.ACTION_VIEW_TASK)
        assertEquals("extra_account_key", AndroidNotificationIntentFactory.EXTRA_ACCOUNT_KEY)
        assertEquals("extra_generation", AndroidNotificationIntentFactory.EXTRA_GENERATION)
        assertEquals("extra_owner_user_id", AndroidNotificationIntentFactory.EXTRA_OWNER_USER_ID)
        assertEquals("extra_row_id", AndroidNotificationIntentFactory.EXTRA_ROW_ID)
        assertEquals("extra_task_id", AndroidNotificationIntentFactory.EXTRA_TASK_ID)
        assertEquals("extra_schedule_token", AndroidNotificationIntentFactory.EXTRA_SCHEDULE_TOKEN)
        assertEquals("extra_occurrence_index", AndroidNotificationIntentFactory.EXTRA_OCCURRENCE_INDEX)
    }

    @Test
    fun testAlarmRequestCodeDeterminismAndDistribution() {
        val accountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        val ref1 = TaskRef("alice", "task-1")
        val ref2 = TaskRef("alice", "task-2")
        val refBob = TaskRef("bob", "task-1")

        val code1 = AndroidNotificationIntentFactory.getAlarmRequestCode(accountKey, ref1, "token-a", 0)
        val code1Repeat = AndroidNotificationIntentFactory.getAlarmRequestCode(accountKey, ref1, "token-a", 0)
        val code2 = AndroidNotificationIntentFactory.getAlarmRequestCode(accountKey, ref1, "token-a", 1)
        val code3 = AndroidNotificationIntentFactory.getAlarmRequestCode(accountKey, ref2, "token-a", 0)
        val codeBob = AndroidNotificationIntentFactory.getAlarmRequestCode(accountKey, refBob, "token-a", 0)

        assertEquals(code1, code1Repeat)
        assertTrue(code1 >= 0, "Request code must be non-negative")
        assertTrue(code2 >= 0, "Request code must be non-negative")
        assertTrue(code3 >= 0, "Request code must be non-negative")
        assertTrue(codeBob >= 0, "Request code must be non-negative")
        assertTrue(code1 != code2, "Different occurrences must produce distinct request codes")
        assertTrue(code1 != code3, "Different tasks must produce distinct request codes")
        assertTrue(code1 != codeBob, "Different owners with identical rowId must produce distinct request codes")
    }

    @Test
    fun testAccountNotificationTagFormatting() {
        val accountKey = AccountKey("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
        val tag = AndroidNotificationIntentFactory.getAccountNotificationTag(accountKey)
        assertEquals("tabbyrush_${accountKey.value}", tag)
    }

    @Test
    fun testAlarmRegistryKeySerialization() {
        val ref = TaskRef("alice", "task-uuid-1234")
        val formatted = AndroidAlarmRegistry.formatAlarmKey(
            generation = 42L,
            taskRef = ref,
            scheduleToken = "tok-xyz",
            occurrenceIndex = 3
        )
        assertEquals("42:alice:task-uuid-1234:tok-xyz:3", formatted)

        val parsed = AndroidAlarmRegistry.parseAlarmKey(formatted)
        assertNotNull(parsed)
        assertEquals(42L, parsed.generation)
        assertEquals(ref, parsed.taskRef)
        assertEquals("task-uuid-1234", parsed.taskId)
        assertEquals("tok-xyz", parsed.scheduleToken)
        assertEquals(3, parsed.occurrenceIndex)
        assertFalse(parsed.isLegacy)

        // Legacy 4-part key without owner is parsed and marked as legacy
        val legacyParsed = AndroidAlarmRegistry.parseAlarmKey("42:task-uuid-1234:tok-xyz:3")
        assertNotNull(legacyParsed)
        assertTrue(legacyParsed.isLegacy)
        assertNull(legacyParsed.taskRef)
        assertEquals("task-uuid-1234", legacyParsed.legacyTaskId)

        // Malformed cases
        assertNull(AndroidAlarmRegistry.parseAlarmKey(""))
        assertNull(AndroidAlarmRegistry.parseAlarmKey("bad:format"))
        assertNull(AndroidAlarmRegistry.parseAlarmKey("notANumber:owner:task:tok:1"))
        assertNull(AndroidAlarmRegistry.parseAlarmKey("1:owner:task:tok:notANumber"))
    }

    @Test
    fun testReminderRestoreReceiverSupportedActions() {
        val actions = ReminderRestoreReceiver.SUPPORTED_ACTIONS
        assertTrue(actions.contains(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(actions.contains(Intent.ACTION_USER_UNLOCKED))
        assertTrue(actions.contains(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(actions.contains(Intent.ACTION_TIME_CHANGED))
        assertTrue(actions.contains(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(actions.contains("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
        assertEquals(6, actions.size)
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
        assertTrue(timingDegraded.isChannelEnabled)

        val notificationsBlocked = AndroidReminderCapabilities(
            canPostNotifications = false,
            canScheduleExactAlarms = true,
            isChannelEnabled = false
        )
        assertFalse(notificationsBlocked.canPostNotifications)
        assertFalse(notificationsBlocked.isChannelEnabled)
    }
}
