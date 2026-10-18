package dev.tabbyrush.android

import android.content.Context
import android.content.Intent

/**
 * Shared constants and intent utilities for server admin announcement push routing.
 * Enforces:
 * - Exact key parsing: type="admin_announcement", appId="tabby-rush", messageId=<canonical UUIDv4>.
 * - Consumed-once target extraction across cold launch (onCreate) and warm update (onNewIntent).
 * - Explicit component-targeted Intent construction without open broadcast routing.
 */
object RemoteNotificationIntents {

    const val ACTION_OPEN_ANNOUNCEMENT = "dev.tabbyrush.android.ACTION_OPEN_ANNOUNCEMENT"

    const val EXTRA_TYPE = "type"
    const val EXTRA_APP_ID = "appId"
    const val EXTRA_MESSAGE_ID = "messageId"
    const val EXTRA_GENERATION = "generation"

    const val EXPECTED_TYPE = "admin_announcement"
    const val EXPECTED_APP_ID = "tabby-rush"

    private val CANONICAL_UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    data class RemoteAnnouncementTarget(
        val messageId: String,
        val generation: Long
    )

    fun isCanonicalUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return CANONICAL_UUID_REGEX.matches(value)
    }

    /**
     * Extracts and safely consumes remote announcement extras from an incoming Intent.
     * Removes the extras immediately to prevent accidental duplicate consumption upon
     * configuration changes or activity re-creations.
     */
    fun parseAndConsume(intent: Intent?): RemoteAnnouncementTarget? {
        if (intent == null) return null

        val type = intent.getStringExtra(EXTRA_TYPE)
        val appId = intent.getStringExtra(EXTRA_APP_ID)
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
        val generation = intent.getLongExtra(EXTRA_GENERATION, -1L)

        // Consume once by removing extras from the intent
        intent.removeExtra(EXTRA_TYPE)
        intent.removeExtra(EXTRA_APP_ID)
        intent.removeExtra(EXTRA_MESSAGE_ID)
        intent.removeExtra(EXTRA_GENERATION)

        if (type != EXPECTED_TYPE || appId != EXPECTED_APP_ID || !isCanonicalUuid(messageId)) {
            return null
        }

        return RemoteAnnouncementTarget(
            messageId = messageId!!,
            generation = generation
        )
    }

    /**
     * Builds an explicit Intent for launching MainActivity when an announcement notification is tapped.
     */
    fun createAnnouncementIntent(
        context: Context,
        messageId: String,
        generation: Long
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ANNOUNCEMENT
            putExtra(EXTRA_TYPE, EXPECTED_TYPE)
            putExtra(EXTRA_APP_ID, EXPECTED_APP_ID)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_GENERATION, generation)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
