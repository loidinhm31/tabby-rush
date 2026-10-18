package dev.tabbyrush.android

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.tabbyrush.shared.contract.RemoteInboxAction
import kotlinx.coroutines.launch

/**
 * Native nonexported Firebase Messaging entry point.
 *
 * Responsibilities:
 * - onNewToken: Forwards newly issued or rotated FCM tokens to the process-wide
 *   RemoteInstallCoordinator. Signed-out tokens are stored privately; signed-in tokens
 *   trigger serialized rotation with a newer generation.
 * - onMessageReceived: Foreground message entry point. Sole local generic post path.
 *   Validates canonical keys and UUID format before posting a fixed generic notification
 *   ("TabbyRush", "You have a new announcement.") via AndroidRemoteNotificationAdapter.
 * - onDeletedMessages: Triggers silent background refresh of the canonical inbox.
 */
class RemoteFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "RemoteFCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = TabbyRushApplication.instance
        app.applicationScope.launch {
            try {
                app.installCoordinator.onNewToken(token)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle new FCM token: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data[RemoteNotificationIntents.EXTRA_TYPE]
        val appId = data[RemoteNotificationIntents.EXTRA_APP_ID]
        val messageId = data[RemoteNotificationIntents.EXTRA_MESSAGE_ID]

        if (type != RemoteNotificationIntents.EXPECTED_TYPE ||
            appId != RemoteNotificationIntents.EXPECTED_APP_ID ||
            !RemoteNotificationIntents.isCanonicalUuid(messageId)
        ) {
            Log.d(TAG, "Ignoring push message with invalid or missing keys")
            return
        }

        val app = TabbyRushApplication.instance
        val activeRuntime = app.getActiveRuntime()
        val generation = activeRuntime?.generation ?: -1L

        // Sole local post path for foreground arrivals
        app.applicationScope.launch {
            try {
                app.remoteNotificationAdapter.show(messageId!!, generation)
                // Silent refresh of active controller inbox state
                activeRuntime?.remoteController?.dispatch(RemoteInboxAction.Refresh)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show generic notification for message $messageId: ${e.message}")
            }
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        val app = TabbyRushApplication.instance
        app.applicationScope.launch {
            try {
                app.getActiveRuntime()?.remoteController?.dispatch(RemoteInboxAction.Refresh)
            } catch (_: Exception) {}
        }
    }
}
