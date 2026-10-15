package dev.doggo.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.doggo.android.AndroidNotificationIntentFactory.ACTION_VIEW_TASK
import dev.doggo.android.AndroidNotificationIntentFactory.EXTRA_ACCOUNT_KEY
import dev.doggo.android.AndroidNotificationIntentFactory.EXTRA_TASK_ID
import dev.doggo.shared.ui.DoggoApp

/**
 * MainActivity is the single exported Android launcher Activity for Doggo.
 * Hosts the shared Compose UI (DoggoApp) and delegates state observation,
 * action dispatching, and notification intent handling to SessionViewModel.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            Surface(modifier = Modifier.fillMaxSize()) {
                DoggoApp(
                    state = uiState,
                    onAction = viewModel::onAction,
                    onAuthSubmit = viewModel::onAuthSubmit
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate permission snapshot on resume
        ReminderPermissionController.evaluateState(this)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null || intent.action != ACTION_VIEW_TASK) return
        val accountKey = intent.getStringExtra(EXTRA_ACCOUNT_KEY)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (!accountKey.isNullOrBlank() && !taskId.isNullOrBlank()) {
            viewModel.selectTaskFromNotification(accountKey, taskId)
        }
    }
}
