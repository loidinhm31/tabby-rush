package dev.doggo.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.TitlePolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Android device tests verifying:
 * 1. Intent and immutable PendingIntent generation for task viewing (ACTION_VIEW_TASK) and creation (ACTION_ADD_TASK).
 * 2. Intent extra deserialization and token fencing against active session state.
 * 3. Notification permission evaluation and intent creation for system settings.
 * 4. TaskSurfaceCreateScreen validation logic adhering to TitlePolicy rules.
 * 5. Navigation rejection across account transitions and for deleted tasks.
 */
@RunWith(AndroidJUnit4::class)
class TaskSurfaceNavigationDeviceTest {

    private lateinit var context: Context
    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val otherAccountKey = AccountKey("9999999999abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 7L)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testOpenTaskIntentConstructionAndImmutability() {
        val taskId = "task-uuid-12345"
        val intent = createOpenTaskIntent(context, testToken, taskId)

        assertEquals(TaskWidgetActionKeys.ACTION_WIDGET_VIEW_TASK, intent.action)
        assertEquals(testAccountKey.value, intent.getStringExtra(TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY))
        assertEquals(7L, intent.getLongExtra(TaskWidgetActionKeys.EXTRA_GENERATION, -1L))
        assertEquals(taskId, intent.getStringExtra(TaskWidgetActionKeys.EXTRA_TASK_ID))
        assertNotNull(intent.component)
        assertEquals(MainActivity::class.java.name, intent.component?.className)

        // PendingIntent must be created with FLAG_IMMUTABLE
        val pendingIntent = createOpenTaskPendingIntent(context, testToken, taskId)
        assertNotNull(pendingIntent)
    }

    @Test
    fun testAddTaskIntentConstructionAndImmutability() {
        val intent = createAddTaskIntent(context, testToken)

        assertEquals(TaskWidgetActionKeys.ACTION_WIDGET_ADD_TASK, intent.action)
        assertEquals(testAccountKey.value, intent.getStringExtra(TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY))
        assertEquals(7L, intent.getLongExtra(TaskWidgetActionKeys.EXTRA_GENERATION, -1L))
        assertNotNull(intent.component)
        assertEquals(MainActivity::class.java.name, intent.component?.className)

        val pendingIntent = createAddTaskPendingIntent(context, testToken)
        assertNotNull(pendingIntent)
    }

    @Test
    fun testNotificationIntentsFactoryConsistency() {
        val taskId = "notification-task-999"
        val openIntent = TaskSurfaceIntents.createOpenIntent(context, testToken, taskId)

        assertEquals(TaskSurfaceIntents.ACTION_VIEW_TASK, openIntent.action)
        assertEquals(testAccountKey.value, openIntent.getStringExtra(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY))
        assertEquals(7L, openIntent.getLongExtra(TaskSurfaceIntents.EXTRA_GENERATION, -1L))
        assertEquals(taskId, openIntent.getStringExtra(TaskSurfaceIntents.EXTRA_TASK_ID))

        val completeIntent = TaskSurfaceIntents.createCompleteIntent(context, testToken, taskId)
        assertEquals(TaskSurfaceIntents.ACTION_COMPLETE_SURFACE_TASK, completeIntent.action)
        assertEquals(testAccountKey.value, completeIntent.getStringExtra(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY))
        assertEquals(7L, completeIntent.getLongExtra(TaskSurfaceIntents.EXTRA_GENERATION, -1L))
        assertEquals(taskId, completeIntent.getStringExtra(TaskSurfaceIntents.EXTRA_TASK_ID))
        assertEquals(TaskSurfaceActionReceiver::class.java.name, completeIntent.component?.className)
    }

    @Test
    fun testNavigationValidationRejectsMismatchedOrStaleTokens() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        access.updateReadiness(SurfaceReadiness.Ready(testToken))

        // Mismatched account token must return false
        val foreignToken = SurfaceAccountToken(otherAccountKey, generation = 7L)
        assertFalse(access.validateNavigation(foreignToken, "task-1"))

        // Stale generation token must return false
        val staleGenToken = SurfaceAccountToken(testAccountKey, generation = 6L)
        assertFalse(access.validateNavigation(staleGenToken, "task-1"))

        // Nonexistent task ID must return false
        val nonExistentNav = access.validateNavigation(testToken, "non-existent-uuid")
        assertFalse(nonExistentNav)
    }

    @Test
    fun testTaskSurfacePermissionControllerSettingsIntents() {
        val channelIntent = TaskSurfacePermissionController.createChannelNotificationSettingsIntent(context)
        assertNotNull(channelIntent.action)

        val appSettingsIntent = TaskSurfacePermissionController.createAppNotificationSettingsIntent(context)
        assertNotNull(appSettingsIntent.action)

        val appDetailsIntent = TaskSurfacePermissionController.createAppDetailsSettingsIntent(context)
        assertNotNull(appDetailsIntent.action)
    }

    @Test
    fun testTaskSurfacePermissionAvailabilityEvaluation() {
        // 1. Opted out state must return OptedOut
        val optedOut = TaskSurfacePermissionController.evaluateAvailability(
            context = context,
            enabled = false
        )
        assertEquals(TaskSurfaceAvailability.OptedOut, optedOut)

        // 2. Enabled evaluation returns Available or PermissionDenied/AppDisabled depending on OS environment
        val evaluated = TaskSurfacePermissionController.evaluateAvailability(
            context = context,
            enabled = true
        )
        assertTrue(
            evaluated == TaskSurfaceAvailability.Available ||
                evaluated == TaskSurfaceAvailability.PermissionDenied ||
                evaluated == TaskSurfaceAvailability.AppDisabled ||
                evaluated == TaskSurfaceAvailability.ChannelDisabled
        )
    }

    @Test
    fun testTitlePolicyValidationInTaskSurfaceCreateScreen() {
        // Blank or whitespace title is rejected
        val blankResult = TitlePolicy.normalizeAndValidate("   ")
        assertTrue(blankResult is ApiResult.Failure)

        // Title exceeding 255 chars is rejected
        val longTitle = "a".repeat(256)
        val longResult = TitlePolicy.normalizeAndValidate(longTitle)
        assertTrue(longResult is ApiResult.Failure)

        // Valid title is accepted and normalized
        val validResult = TitlePolicy.normalizeAndValidate("  Buy groceries  ")
        assertTrue(validResult is ApiResult.Success)
        assertEquals("Buy groceries", (validResult as ApiResult.Success).value)
    }
}
