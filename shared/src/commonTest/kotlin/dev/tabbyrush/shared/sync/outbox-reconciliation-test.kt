package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.Todo
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Durable FIFO, ack and restart behavior is exercised against BundledSQLite in jvmTest. */
class OutboxReconciliationTest {
    @Test fun frozenEnvelopeRoundTripRetainsOpaqueValuesAndExplicitNull() {
        val raw = TaskJsonMerge.encode(Todo("a", "Title", false, 1L)).toMutableMap().apply {
            put("future", TaskJsonMerge.json.parseToJsonElement("""{"nested":[null,1,{"enabled":true}]}"""))
        }
        val envelope = PushRecordDto(rowId = "a", data = kotlinx.serialization.json.JsonObject(raw), version = 8L, assumedServerVersion = 7L)
        val serialized = TaskJsonMerge.json.encodeToString(envelope)
        val recovered = TaskJsonMerge.json.decodeFromString<PushRecordDto>(serialized)
        assertEquals(envelope, recovered)
        assertFalse(recovered.deleted)
        assertEquals(7L, recovered.assumedServerVersion)
    }
}
