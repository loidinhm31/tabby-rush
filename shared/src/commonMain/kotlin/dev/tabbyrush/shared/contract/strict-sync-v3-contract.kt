package dev.tabbyrush.shared.contract

object StrictSyncV3Contract {
    const val PROTOCOL_VERSION = "strict-v3"
    const val DEFAULT_TABLE_NAME = "todos"

    // Canonical schema fingerprints for appId "tabby-rush"
    const val SCHEMA_FINGERPRINT_V1 = "132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8"
    const val SCHEMA_FINGERPRINT_V2 = "8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653"
    const val SCHEMA_FINGERPRINT_V3 = "7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f"

    // Legacy schema fingerprints for appId "doggo" (historical backwards-compatibility)
    const val LEGACY_DOGGO_SCHEMA_FINGERPRINT_V1 = "3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec"
    const val LEGACY_DOGGO_SCHEMA_FINGERPRINT_V2 = "5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4"
    const val LEGACY_DOGGO_SCHEMA_FINGERPRINT_V3 = "eec72663e7367b4330d1c94196f8e44888df02fa34e240fa4d3bd29222136094"

    const val MIN_REPEAT_INTERVAL_MINUTES = 15L
    const val MIN_REMINDER_COUNT = 1L
    const val MAX_REMINDER_COUNT = 10L

    fun getFingerprintForApp(appId: String, version: String): String {
        return when (appId) {
            "tabby-rush" -> when (version) {
                "strict-v1" -> SCHEMA_FINGERPRINT_V1
                "strict-v2" -> SCHEMA_FINGERPRINT_V2
                "strict-v3" -> SCHEMA_FINGERPRINT_V3
                else -> error("Unknown protocol version: $version")
            }
            "doggo" -> when (version) {
                "strict-v1" -> LEGACY_DOGGO_SCHEMA_FINGERPRINT_V1
                "strict-v2" -> LEGACY_DOGGO_SCHEMA_FINGERPRINT_V2
                "strict-v3" -> LEGACY_DOGGO_SCHEMA_FINGERPRINT_V3
                else -> error("Unknown protocol version: $version")
            }
            else -> error("Unknown appId: $appId")
        }
    }

    fun isCanonicalUuid(s: String): Boolean {
        if (s.length != 36) return false
        for (i in 0 until 36) {
            val c = s[i]
            when (i) {
                8, 13, 18, 23 -> if (c != '-') return false
                else -> if (c !in '0'..'9' && c !in 'a'..'f') return false
            }
        }
        return true
    }
}

data class StrictV3TodoData(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val deadlineDate: String? = null,
    val deadlineTime: String? = null,
    val deadlineZone: String? = null,
    val deadlineOffsetSeconds: Long? = null,
    val reminderAnchorTime: String? = null,
    val deadlineAnchorEpochMs: Long? = null,
    val deadlineOverdueEpochMs: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Long = 0L,
    val reminderIntervalMinutes: Long? = null,
    val reminderCount: Long = 1L,
    val scheduleToken: String? = null,
    val parentTaskId: String? = null
) {
    fun validate() {
        require(id.isNotEmpty()) { "id must not be empty" }
        require(createdAt >= 0) { "createdAt must be non-negative" }
        require(reminderLeadMinutes >= 0) { "reminderLeadMinutes must be non-negative" }
        require(reminderCount in StrictSyncV3Contract.MIN_REMINDER_COUNT..StrictSyncV3Contract.MAX_REMINDER_COUNT) {
            "reminderCount must be between 1 and 10"
        }
        if (reminderCount == 1L) {
            require(reminderIntervalMinutes == null) {
                "reminderIntervalMinutes must be null when reminderCount is 1"
            }
        } else {
            require(reminderIntervalMinutes != null && reminderIntervalMinutes >= StrictSyncV3Contract.MIN_REPEAT_INTERVAL_MINUTES) {
                "repeating reminderIntervalMinutes must be >= 15"
            }
        }
        if (deadlineDate == null) {
            require(deadlineTime == null) { "deadlineTime must be null when deadlineDate is null" }
            require(deadlineZone == null) { "deadlineZone must be null when deadlineDate is null" }
            require(deadlineOffsetSeconds == null) { "deadlineOffsetSeconds must be null when deadlineDate is null" }
            require(reminderAnchorTime == null) { "reminderAnchorTime must be null when deadlineDate is null" }
            require(deadlineAnchorEpochMs == null) { "deadlineAnchorEpochMs must be null when deadlineDate is null" }
            require(deadlineOverdueEpochMs == null) { "deadlineOverdueEpochMs must be null when deadlineDate is null" }
            require(scheduleToken == null) { "scheduleToken must be null when deadlineDate is null" }
            require(!reminderEnabled) { "reminderEnabled must be false when deadlineDate is null" }
            require(reminderLeadMinutes == 0L) { "reminderLeadMinutes must be 0 when deadlineDate is null" }
            require(reminderCount == 1L) { "reminderCount must be 1 when deadlineDate is null" }
            require(reminderIntervalMinutes == null) { "reminderIntervalMinutes must be null when deadlineDate is null" }
        } else {
            require(deadlineZone != null) { "deadlineZone must not be null when deadlineDate is present" }
            require(deadlineOffsetSeconds != null) { "deadlineOffsetSeconds must not be null when deadlineDate is present" }
            require(deadlineAnchorEpochMs != null) { "deadlineAnchorEpochMs must not be null when deadlineDate is present" }
            require(deadlineOverdueEpochMs != null) { "deadlineOverdueEpochMs must not be null when deadlineDate is present" }
            require(scheduleToken != null) { "scheduleToken must not be null when deadlineDate is present" }
            if (deadlineTime == null) {
                require(reminderAnchorTime != null) { "reminderAnchorTime must not be null for date-only deadline" }
            } else {
                require(reminderAnchorTime == null) { "reminderAnchorTime must be null for timed deadline" }
            }
            if (reminderEnabled) {
                val anchor = deadlineAnchorEpochMs
                val leadMs = reminderLeadMinutes * 60_000L
                val firstAnchor = anchor - leadMs
                val intervalMs = (reminderIntervalMinutes ?: 0L) * 60_000L
                val lastOffset = (reminderCount - 1L) * intervalMs
                val lastAnchor = firstAnchor + lastOffset
                require(lastAnchor <= anchor) { "last notification anchor ($lastAnchor) must be <= deadlineAnchorEpochMs ($anchor)" }
            }
        }
        if (parentTaskId != null) {
            require(parentTaskId.isNotBlank()) { "parentTaskId must not be blank" }
            require(parentTaskId != id) { "parentTaskId must not equal id (self-reference forbidden)" }
            require(StrictSyncV3Contract.isCanonicalUuid(parentTaskId)) { "parentTaskId must be a canonical lowercase UUIDv4" }
        }
    }
}
