package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.data.LegacySyncEvidenceEntity
import kotlinx.serialization.json.*
import kotlin.test.*

class LegacyStandardIntentMigrationTest {
    private fun evidence(history: String?, state: String = "PENDING", checkpoint: String? = null): LegacySyncEvidenceEntity {
        val raw = buildJsonObject {
            put("opId", "old"); put("rowId", "a"); put("kind", "update")
            put("expectedRevision", 1L); put("contractVersion", "strict-v3")
            put("deliveryState", state); put("consecutiveFailures", 0L); put("intentMask", "TITLE")
            put("dispatchHistory", history?.let(::JsonPrimitive) ?: JsonNull)
            put("transitionCheckpoint", checkpoint?.let(::JsonPrimitive) ?: JsonNull)
        }
        return LegacySyncEvidenceEntity(sourceOpId = "old", rowId = "a", originalJson = raw.toString())
    }

    @Test fun pendingAndAbsentHistoryAreNotProofOfNonDispatch() {
        assertFalse(LegacyStandardIntentMigration.provablyNeverDispatched(evidence(null)))
        assertFalse(LegacyStandardIntentMigration.provablyNeverDispatched(evidence("""{"event":"ROOM_V1_MIGRATION","priorState":"PENDING"}""", checkpoint = "MIGRATED_FROM_ROOM_V1")))
        assertFalse(LegacyStandardIntentMigration.provablyNeverDispatched(evidence("""{"dispatched":true}""")))
    }

    @Test fun onlyExplicitCompleteZeroDispatchHistoryIsEligible() {
        val history = """{"createdUnsent":true,"dispatchCount":0}"""
        assertTrue(LegacyStandardIntentMigration.provablyNeverDispatched(evidence(history)))
        assertFalse(LegacyStandardIntentMigration.provablyNeverDispatched(evidence(history, state = "UNCERTAIN")))
        assertFalse(LegacyStandardIntentMigration.provablyNeverDispatched(evidence(history, checkpoint = "PROVEN_NOT_ACCEPTED")))
        assertFalse(LegacyStandardIntentMigration.provablyNeverDispatched(evidence("""{"createdUnsent":true,"dispatchCount":0,"unknown":true}""")))
    }
}
