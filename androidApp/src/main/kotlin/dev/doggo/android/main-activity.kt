package dev.doggo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.doggo.shared.ui.DoggoApp

/**
 * MainActivity is the single exported Android launcher Activity for Doggo.
 * Hosts the shared Compose UI (DoggoApp) and delegates state observation
 * and action dispatching to SessionViewModel.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}
