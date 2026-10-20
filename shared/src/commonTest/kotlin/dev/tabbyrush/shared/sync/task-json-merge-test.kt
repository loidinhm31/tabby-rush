package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.*
import kotlinx.serialization.json.*
import kotlin.test.*

class TaskJsonMergeTest {
    @Test fun knownEditPreservesNestedUnknownAndExplicitNulls() {
        val remote = JsonObject(TaskJsonMerge.encode(Todo("a", "Remote", false, 1L)).toMutableMap().apply {
            put("future", TaskJsonMerge.json.parseToJsonElement("""{"items":[null,{"x":1.25}],"nullable":null}"""))
        })
        val intent = TaskJsonMerge.encode(Todo("a", "Local", true, 1L))
        val merged = TaskJsonMerge.merge(remote, intent, "TITLE")
        assertEquals(remote["future"], merged["future"])
        assertEquals(JsonPrimitive("Local"), merged["title"])
        assertEquals(JsonPrimitive(false), merged["completed"])
        assertEquals(JsonNull, merged["deadlineDate"])
    }

    @Test fun unknownMaskNeverOverwritesRemote() {
        val data = TaskJsonMerge.encode(Todo("a", "Title", false, 1L))
        assertFailsWith<IllegalArgumentException> { TaskJsonMerge.merge(data, data, null) }
        assertFailsWith<IllegalArgumentException> { TaskJsonMerge.merge(data, data, "REQUIRES_REVIEW") }
    }

    @Test fun malformedKnownFieldsRetainRawButNeverSchedule() {
        val raw = JsonObject(TaskJsonMerge.encode(Todo("a", "Title", false, 1L)).toMutableMap().apply { put("completed", JsonPrimitive("false")) })
        val base = TaskJsonMerge.project(PullRecordDto("todos", "a", raw, 1L, "2026-10-20T00:00:00Z"))
        assertNotNull(base.projectionError)
        assertEquals(raw.toString(), base.rawJson)
        assertTrue(dev.tabbyrush.shared.contract.PersistedScheduleProjector.computeProjection(listOf(base), emptyList()).isEmpty())
    }

    @Test fun tombstoneNeedsNoTaskDecode() {
        val raw = buildJsonObject { put("retained", "opaque") }
        val base = TaskJsonMerge.project(PullRecordDto("todos", "a", raw, 2L, "2026-10-20T00:00:00Z", true))
        assertTrue(base.deleted)
        assertNull(base.projectionError)
        assertEquals(raw.toString(), base.rawJson)
        val empty = TaskJsonMerge.project(PullRecordDto("todos", "a", JsonNull, 3L, "2026-10-20T00:00:00Z", true))
        assertTrue(empty.deleted)
        assertNull(empty.projectionError)
        assertEquals("null", empty.rawJson)
    }

    @Test fun taskDataRoundTripAcrossPackagesRetainsPrivateValidation() {
        val todo = Todo("a", "Local", false, 1L)
        assertEquals(todo.toTaskData(), TaskJsonMerge.decode(todo.id, TaskJsonMerge.encode(todo)))
        val invalid = JsonObject(TaskJsonMerge.encode(todo).toMutableMap().apply { put("createdAt", JsonPrimitive(-1L)) })
        assertFailsWith<IllegalArgumentException> { TaskJsonMerge.decode(todo.id, invalid) }
    }

    @Test fun contradictoryCivilEpochAndOffsetRemainRawAndUnschedulable() {
        val resolver = StrictTimeZoneResolver.default
        val anchor = resolver.resolveInstant("UTC", "2026-10-20", "10:00", 0L)
        val deadline = TaskDeadline("2026-10-20", "10:00", "UTC", 0L, anchorEpochMs = anchor, overdueEpochMs = anchor)
        val raw = TaskJsonMerge.encode(Todo("a", "Timed", false, 1L, deadline = deadline,
            reminderRule = ReminderRule(enabled = true), scheduleToken = "token"))
        assertEquals(anchor, TaskJsonMerge.decode("a", raw).deadlineAnchorEpochMs)
        for (changes in listOf(
            mapOf("deadlineAnchorEpochMs" to JsonPrimitive(anchor + 86_400_000L), "deadlineOverdueEpochMs" to JsonPrimitive(anchor + 86_400_000L)),
            mapOf("deadlineOffsetSeconds" to JsonPrimitive(3600L))
        )) {
            assertIncompatible(JsonObject(raw.toMutableMap().apply { putAll(changes) }))
        }
    }

    @Test fun dateOnlyUsesNextCalendarMidnightRatherThanFixedDay() {
        val resolver = StrictTimeZoneResolver.default
        val anchor = resolver.resolveInstant("America/New_York", "2026-11-01", "00:30", -14_400L)
        val overdue = resolver.computeNextCalendarDayStartEpochMs("America/New_York", "2026-11-01")
        val deadline = TaskDeadline("2026-11-01", zone = "America/New_York", offsetSeconds = -14_400L,
            reminderAnchorTime = "00:30", anchorEpochMs = anchor, overdueEpochMs = overdue)
        val raw = TaskJsonMerge.encode(Todo("a", "Date only", false, 1L, deadline = deadline, scheduleToken = "token"))
        assertEquals(overdue, TaskJsonMerge.decode("a", raw).deadlineOverdueEpochMs)
        assertIncompatible(JsonObject(raw.toMutableMap().apply { put("deadlineOverdueEpochMs", JsonPrimitive(anchor + 86_400_000L)) }))
    }

    @Test fun overlapHonorsEitherCapturedOffsetButRejectsCrossedAnchor() {
        val resolver = StrictTimeZoneResolver.default
        val overlap = resolver.resolveOffset("America/New_York", "2026-11-01", "01:30") as StrictZoneOffsetResult.Overlap
        for ((offset, anchor) in listOf(overlap.earlierOffsetSeconds to overlap.earlierEpochMs, overlap.laterOffsetSeconds to overlap.laterEpochMs)) {
            val deadline = TaskDeadline("2026-11-01", "01:30", "America/New_York", offset,
                anchorEpochMs = anchor, overdueEpochMs = anchor)
            val raw = TaskJsonMerge.encode(Todo("a", "Overlap", false, 1L, deadline = deadline, scheduleToken = "token"))
            assertEquals(anchor, TaskJsonMerge.decode("a", raw).deadlineAnchorEpochMs)
            val other = if (anchor == overlap.earlierEpochMs) overlap.laterEpochMs else overlap.earlierEpochMs
            assertIncompatible(JsonObject(raw.toMutableMap().apply {
                put("deadlineAnchorEpochMs", JsonPrimitive(other)); put("deadlineOverdueEpochMs", JsonPrimitive(other))
            }))
        }
    }

    private fun assertIncompatible(raw: JsonObject) {
        val base = TaskJsonMerge.project(PullRecordDto("todos", "a", raw, 1L, "2026-10-20T00:00:00Z"))
        assertNotNull(base.projectionError)
        assertEquals(raw.toString(), base.rawJson)
        assertTrue(PersistedScheduleProjector.computeProjection(listOf(base), emptyList()).isEmpty())
    }
}
