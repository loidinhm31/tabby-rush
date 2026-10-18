package dev.tabbyrush.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import dev.tabbyrush.shared.ui.TabbyRushApp
import dev.tabbyrush.shared.contract.TodoAction

/**
 * MainActivity is the single exported Android launcher Activity for TabbyRush.
 * Hosts the shared Compose UI (TabbyRushApp) and handles:
 * - Direct ACTION_VIEW_TASK and ACTION_ADD_TASK intent routing with token fencing.
 * - POST_NOTIFICATIONS runtime permission requests via ActivityResult contracts.
 * - Integration of compact task creation and task surface settings.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onNotificationPermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val remoteInboxState by viewModel.remoteInboxState.collectAsState()
            val surfaceCreateVisible by viewModel.surfaceCreateVisible.collectAsState()
            val surfaceSettingsVisible by viewModel.surfaceSettingsVisible.collectAsState()
            val surfacePrefState by viewModel.surfacePreferenceState.collectAsState()
            val surfaceAvailability by viewModel.surfaceAvailability.collectAsState()
            Surface(modifier = Modifier.fillMaxSize()) {
                TabbyRushApp(
                    state = uiState,
                    remoteInboxState = remoteInboxState,
                    onAction = { action ->
                        when (action) {
                            is TodoAction.OpenNotificationSettings -> {
                                try {
                                    startActivity(
                                        ReminderPermissionController.createChannelNotificationSettingsIntent(
                                            this@MainActivity
                                        )
                                    )
                                } catch (_: Exception) {
                                    try {
                                        startActivity(
                                            ReminderPermissionController.createAppNotificationSettingsIntent(
                                                this@MainActivity
                                            )
                                        )
                                    } catch (_: Exception) {
                                        startActivity(
                                            ReminderPermissionController.createAppDetailsSettingsIntent(
                                                this@MainActivity
                                            )
                                        )
                                    }
                                }
                            }
                            is TodoAction.RequestExactAlarmPermission -> {
                                try {
                                    startActivity(
                                        ReminderPermissionController.createExactAlarmSettingsIntent(
                                            this@MainActivity
                                        )
                                    )
                                } catch (_: Exception) {
                                    startActivity(
                                        ReminderPermissionController.createAppDetailsSettingsIntent(
                                            this@MainActivity
                                        )
                                    )
                                }
                            }
                            else -> viewModel.onAction(action)
                        }
                    },
                    onRemoteAction = viewModel::onRemoteAction,
                    onAuthSubmit = viewModel::onAuthSubmit
                )

                if (surfaceCreateVisible) {
                    TaskSurfaceCreateScreen(
                        onDismiss = { viewModel.closeSurfaceCreate() },
                        onSave = { rawTitle -> viewModel.createTaskFromSurface(rawTitle) }
                    )
                }

                if (surfaceSettingsVisible) {
                    Dialog(onDismissRequest = { viewModel.closeSurfaceSettings() }) {
                        TaskSurfaceSettings(
                            enabled = surfacePrefState.enabled,
                            availability = surfaceAvailability,
                            onEnabledChange = { enabled -> viewModel.setSurfaceNotificationsEnabled(enabled) },
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenSystemSettings = {
                                try {
                                    startActivity(
                                        TaskSurfacePermissionController.createChannelNotificationSettingsIntent(
                                            this@MainActivity
                                        )
                                    )
                                } catch (_: Exception) {
                                    startActivity(
                                        TaskSurfacePermissionController.createAppNotificationSettingsIntent(
                                            this@MainActivity
                                        )
                                    )
                                }
                            },
                            onClose = { viewModel.closeSurfaceSettings() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate reminder permissions
        ReminderPermissionController.evaluateState(this)
        // Re-evaluate surface capabilities
        viewModel.onActivityResume()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // Safe consumed-once announcement push intent handling
        val remoteTarget = RemoteNotificationIntents.parseAndConsume(intent)
        if (remoteTarget != null) {
            viewModel.selectAnnouncementFromIntent(remoteTarget.messageId, remoteTarget.generation)
            return
        }

        val action = intent.action ?: return
        when (action) {
            TaskWidgetActionKeys.ACTION_WIDGET_VIEW_TASK,
            TaskSurfaceIntents.ACTION_VIEW_TASK,
            ACTION_VIEW_TASK_LEGACY,
            ACTION_OPEN_TASK -> {
                val accountKey = intent.getStringExtra(TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY)
                    ?: intent.getStringExtra(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY)
                    ?: intent.getStringExtra(AndroidNotificationIntentFactory.EXTRA_ACCOUNT_KEY)
                val generation = intent.getLongExtra(
                    TaskWidgetActionKeys.EXTRA_GENERATION,
                    intent.getLongExtra(TaskSurfaceIntents.EXTRA_GENERATION, -1L)
                )
                val taskId = intent.getStringExtra(TaskWidgetActionKeys.EXTRA_TASK_ID)
                    ?: intent.getStringExtra(TaskSurfaceIntents.EXTRA_TASK_ID)
                    ?: intent.getStringExtra(AndroidNotificationIntentFactory.EXTRA_TASK_ID)

                if (!taskId.isNullOrBlank()) {
                    val isSurfaceAction = action != ACTION_VIEW_TASK_LEGACY
                    viewModel.selectTaskFromIntent(accountKey, generation, taskId, isSurfaceAction)
                }
            }

            TaskWidgetActionKeys.ACTION_WIDGET_ADD_TASK,
            ACTION_ADD_TASK,
            ACTION_CREATE_TASK -> {
                val accountKey = intent.getStringExtra(TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY)
                    ?: intent.getStringExtra(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY)
                val generation = intent.getLongExtra(
                    TaskWidgetActionKeys.EXTRA_GENERATION,
                    intent.getLongExtra(TaskSurfaceIntents.EXTRA_GENERATION, -1L)
                )
                viewModel.openCreateFromIntent(accountKey, generation)
            }
        }
    }

    companion object {
        const val ACTION_VIEW_TASK_LEGACY = "dev.tabbyrush.android.ACTION_VIEW_TASK"
        const val ACTION_OPEN_TASK = "dev.tabbyrush.android.ACTION_OPEN"
        const val ACTION_ADD_TASK = "dev.tabbyrush.android.ACTION_ADD_TASK"
        const val ACTION_CREATE_TASK = "dev.tabbyrush.android.ACTION_CREATE"
    }
}
