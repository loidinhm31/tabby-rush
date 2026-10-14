package dev.doggo.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.theme.DoggoThemeTokens
import dev.doggo.shared.auth.HubAuthValidation

/**
 * Transient actions for authentication.
 * Overrides toString() to ensure passwords are NEVER leaked into logs, crash dumps, or memory inspections.
 */
sealed interface AuthSubmitAction {
    class Login(val email: String, val password: String) : AuthSubmitAction {
        override fun toString(): String = "Login(email=$email, password=[REDACTED])"
    }

    class Register(val username: String, val email: String, val password: String) : AuthSubmitAction {
        override fun toString(): String = "Register(username=$username, email=$email, password=[REDACTED])"
    }

    data class RequestAccess(val message: String?) : AuthSubmitAction
    data object RefreshStatus : AuthSubmitAction
    data object Logout : AuthSubmitAction
}

enum class AccountFormMode {
    LOGIN,
    SIGNUP
}

@Composable
fun AccountForm(
    authState: AuthState,
    safeError: SafeError?,
    onAuthSubmit: (AuthSubmitAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(AccountFormMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var clientValidationError by remember { mutableStateOf<String?>(null) }

    // Password state is strictly transient in memory, NEVER preserved across configuration changes
    var password by remember { mutableStateOf("") }

    // Wiping password reference on disposal/unmount
    DisposableEffect(Unit) {
        onDispose {
            password = ""
        }
    }

    val isSubmitting = authState == AuthState.AUTHENTICATING

    val canSubmit = !isSubmitting && email.isNotBlank() && password.isNotBlank() &&
            (mode == AccountFormMode.LOGIN || username.isNotBlank())

    val submit = {
        if (canSubmit) {
            val trimmedEmail = email.trim()
            val trimmedUsername = username.trim()
            val passToSubmit = password

            if (mode == AccountFormMode.SIGNUP && !HubAuthValidation.validateUsername(trimmedUsername)) {
                clientValidationError = "Username must be 3–32 characters (letters, digits, _, -)"
            } else if (!HubAuthValidation.validateEmail(trimmedEmail)) {
                clientValidationError = "Please enter a valid email address"
            } else if (!HubAuthValidation.validatePassword(passToSubmit)) {
                clientValidationError = "Password must be at least 8 characters"
            } else {
                clientValidationError = null
                password = "" // clear transient state immediately upon submission
                if (mode == AccountFormMode.LOGIN) {
                    onAuthSubmit(AuthSubmitAction.Login(trimmedEmail, passToSubmit))
                } else {
                    onAuthSubmit(AuthSubmitAction.Register(trimmedUsername, trimmedEmail, passToSubmit))
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(DoggoThemeTokens.SpacingLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(DoggoThemeTokens.SpacingLarge)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (mode == AccountFormMode.LOGIN) "Sign in to Doggo" else "Create your account",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingSmall))

            Text(
                text = if (mode == AccountFormMode.LOGIN) {
                    "Connect with your Hub credentials"
                } else {
                    "Username (3–32 bytes), Password (8–128 bytes)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = DoggoThemeTokens.customColors.muted
            )

            val errorText = clientValidationError ?: safeError?.message
            if (errorText != null) {
                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingLarge))

            if (mode == AccountFormMode.SIGNUP) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; clientValidationError = null },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                )
                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; clientValidationError = null },
                label = { Text("Email address") },
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; clientValidationError = null },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    .semantics {
                        password()
                    }
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXLarge))

            Button(
                onClick = submit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.small
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    )
                } else {
                    Text(
                        text = if (mode == AccountFormMode.LOGIN) "Sign In" else "Create Account",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        mode = if (mode == AccountFormMode.LOGIN) AccountFormMode.SIGNUP else AccountFormMode.LOGIN
                        password = "" // clear on mode switch
                        clientValidationError = null
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                ) {
                    Text(
                        text = if (mode == AccountFormMode.LOGIN) {
                            "Need an account? Sign up"
                        } else {
                            "Already have an account? Sign in"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DoggoThemeTokens.customColors.primaryDeep
                    )
                }
            }
        }
    }
}
