package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.ServerBaseEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Only locally changed known fields replace values. Opaque JSON is never round-tripped through TaskData. */
object TaskJsonMerge {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private val deadlineKeys = setOf("deadlineDate", "deadlineTime", "deadlineZone", "deadlineOffsetSeconds", "reminderAnchorTime", "deadlineAnchorEpochMs", "deadlineOverdueEpochMs", "scheduleToken")
    private val reminderKeys = setOf("reminderEnabled", "reminderLeadMinutes", "reminderIntervalMinutes", "reminderCount", "scheduleToken")
    val masks = setOf("TITLE", "COMPLETED", "DEADLINE", "REMINDER", "PARENT", "DELETE", "LAST_CHILD_PRESERVATION")

    fun encode(todo: Todo): JsonObject = json.encodeToJsonElement(todo.toTaskData()).jsonObject

    fun merge(base: JsonObject, intent: JsonObject, mask: String?): JsonObject {
        val parts = requireNotNull(mask) { "Unknown local intent" }.split('|').toSet()
        require(parts.isNotEmpty() && parts.all { it in masks }) { "Unknown local intent mask" }
        val keys = mutableSetOf<String>()
        if ("TITLE" in parts) keys.add("title")
        if ("COMPLETED" in parts) keys.add("completed")
        if ("DEADLINE" in parts) keys.addAll(deadlineKeys + reminderKeys)
        if ("REMINDER" in parts) keys.addAll(reminderKeys)
        if ("PARENT" in parts) keys.add("parentTaskId")
        return JsonObject(base.toMutableMap().apply {
            for (key in keys) {
                put(key, intent[key] ?: JsonNull)
            }
        })
    }

    fun decodeRelation(rowId: String, raw: JsonObject): String? {
        val id = raw["id"]?.let { if (it is JsonPrimitive && it.isString) it.content else null }
        val taskId = raw["taskId"]?.let { if (it is JsonPrimitive && it.isString) it.content else null }
        require(id == rowId) { "Relation id mismatch: expected $rowId, got $id" }
        require(taskId == rowId) { "Relation taskId mismatch: expected $rowId, got $taskId" }
        val userEl = raw["userId"]
        return if (userEl == null || userEl is JsonNull) null
        else if (userEl is JsonPrimitive && userEl.isString) userEl.content
        else throw IllegalArgumentException("Invalid userId in relation")
    }

    fun encodeRelation(taskId: String, recipientUserId: String?): JsonObject = buildJsonObject {
        put("id", taskId)
        put("taskId", taskId)
        if (recipientUserId != null) {
            put("userId", recipientUserId)
        } else {
            put("userId", JsonNull)
        }
    }

    fun decode(rowId: String, raw: JsonObject): TaskData {
        for (key in setOf("id", "title")) require(raw[key] is JsonPrimitive && raw[key]!!.jsonPrimitive.isString) { "Invalid $key" }
        for (key in setOf("completed", "reminderEnabled")) {
            val value = raw[key]?.jsonPrimitive
            require(value != null && !value.isString && value.booleanOrNull != null) { "Invalid $key" }
        }
        for (key in setOf("createdAt", "reminderLeadMinutes", "reminderCount")) {
            val value = raw[key]?.jsonPrimitive
            require(value != null && !value.isString && value.longOrNull != null) { "Invalid $key" }
        }
        for (key in setOf("deadlineDate", "deadlineTime", "deadlineZone", "reminderAnchorTime", "scheduleToken", "parentTaskId")) {
            val value = raw[key] ?: continue
            require(value == JsonNull || (value is JsonPrimitive && value.isString)) { "Invalid $key" }
        }
        for (key in setOf("deadlineOffsetSeconds", "deadlineAnchorEpochMs", "deadlineOverdueEpochMs", "reminderIntervalMinutes")) {
            val value = raw[key] ?: continue
            require(value == JsonNull || (value is JsonPrimitive && !value.isString && value.longOrNull != null)) { "Invalid $key" }
        }
        val data = json.decodeFromJsonElement<TaskData>(raw)
        require(data.id == rowId) { "Task identity mismatch" }
        data.validate()
        validateDeadlineInstants(data)
        data.toTodo() // includes checked reminder arithmetic
        return data
    }

    private fun validateDeadlineInstants(data: TaskData) {
        val date = data.deadlineDate ?: return
        val zone = requireNotNull(data.deadlineZone)
        val time = requireNotNull(data.deadlineTime ?: data.reminderAnchorTime)
        val offset = requireNotNull(data.deadlineOffsetSeconds)
        val resolver = StrictTimeZoneResolver.default
        // Resolver rejects gaps/invalid offsets and honors the captured offset in a DST overlap.
        val anchor = resolver.resolveInstant(zone, date, time, offset)
        require(data.deadlineAnchorEpochMs == anchor) { "Deadline anchor does not match civil time" }
        val overdue = if (data.deadlineTime != null) anchor else resolver.computeNextCalendarDayStartEpochMs(zone, date)
        require(data.deadlineOverdueEpochMs == overdue) { "Deadline overdue boundary does not match civil date" }
    }

    fun project(record: PullRecordDto): ServerBaseEntity {
        require((record.tableName == "todos" || record.tableName == "taskAssignees") && record.rowId.isNotEmpty()) {
            "Unexpected record identity: table=${record.tableName}, rowId=${record.rowId}"
        }
        require(record.version in 1..MAX_EXACT_VERSION) { "Invalid record version" }
        val owner = record.ownerUserId
        val boundKey = if (owner != null && owner.isNotBlank()) dev.tabbyrush.shared.data.boundBindingKey(owner) else dev.tabbyrush.shared.data.BINDING_LOCAL_ACCOUNT
        val bindingState = if (owner != null && owner.isNotBlank()) dev.tabbyrush.shared.data.BINDING_BOUND else dev.tabbyrush.shared.data.BINDING_LOCAL_ACCOUNT

        if (record.tableName == "taskAssignees") {
            val relation = if (record.deleted) null else runCatching { decodeRelation(record.rowId, record.data.jsonObject) }.getOrNull()
            return ServerBaseEntity(
                bindingKey = boundKey,
                ownerBinding = bindingState,
                ownerUserId = owner,
                tableName = record.tableName,
                rowId = record.rowId,
                serverRevision = record.version,
                deleted = record.deleted,
                rawJson = record.data.toString(),
                syncedAt = record.syncedAt,
                standardConfirmed = true,
                projectionError = if (!record.deleted && relation == null && record.data !is JsonNull) "Remote relation fields are incompatible; raw data retained" else null
            )
        }

        val data = if (record.deleted) null else runCatching { decode(record.rowId, record.data.jsonObject) }.getOrNull()
        return ServerBaseEntity(
            bindingKey = boundKey,
            ownerBinding = bindingState,
            ownerUserId = owner,
            tableName = record.tableName,
            rowId = record.rowId,
            serverRevision = record.version,
            deleted = record.deleted,
            title = data?.title ?: "",
            completed = data?.completed ?: false,
            createdAt = data?.createdAt ?: 0L,
            updatedAt = data?.createdAt ?: 0L,
            deadlineDate = data?.deadlineDate,
            deadlineTime = data?.deadlineTime,
            deadlineZone = data?.deadlineZone,
            deadlineOffsetSeconds = data?.deadlineOffsetSeconds,
            reminderAnchorTime = data?.reminderAnchorTime,
            deadlineAnchorEpochMs = data?.deadlineAnchorEpochMs,
            deadlineOverdueEpochMs = data?.deadlineOverdueEpochMs,
            reminderEnabled = data?.reminderEnabled ?: false,
            reminderLeadMinutes = data?.reminderLeadMinutes ?: 0L,
            reminderIntervalMinutes = data?.reminderIntervalMinutes,
            reminderCount = data?.reminderCount ?: 1L,
            scheduleToken = data?.scheduleToken,
            parentTaskId = data?.parentTaskId,
            rawJson = record.data.toString(),
            syncedAt = record.syncedAt,
            standardConfirmed = true,
            projectionError = if (!record.deleted && data == null) "Remote task fields are incompatible; raw data retained for review" else null
        )
    }
}

fun TaskData.toTodo(ownerUserId: String = "local"): Todo = Todo(
    taskRef = TaskRef(if (ownerUserId.isNotBlank()) ownerUserId else "local", id),
    title = title,
    completed = completed,
    createdAt = createdAt,
    deadline = toDeadline(),
    reminderRule = toReminderRule(),
    scheduleToken = scheduleToken,
    parentTaskRef = parentTaskId?.let { TaskRef(if (ownerUserId.isNotBlank()) ownerUserId else "local", it) }
)

fun OutboxEntity.intentJson(): JsonObject = if (payloadJson != "{}") TaskJsonMerge.json.parseToJsonElement(payloadJson).jsonObject else {
    TaskJsonMerge.json.encodeToJsonElement(TaskData(
        id = rowId, title = title ?: "", completed = completed ?: false, createdAt = createdAt ?: 0L,
        deadlineDate = deadlineDate, deadlineTime = deadlineTime, deadlineZone = deadlineZone,
        deadlineOffsetSeconds = deadlineOffsetSeconds, reminderAnchorTime = reminderAnchorTime,
        deadlineAnchorEpochMs = deadlineAnchorEpochMs, deadlineOverdueEpochMs = deadlineOverdueEpochMs,
        reminderEnabled = reminderEnabled, reminderLeadMinutes = reminderLeadMinutes,
        reminderIntervalMinutes = reminderIntervalMinutes, reminderCount = reminderCount,
        scheduleToken = scheduleToken, parentTaskId = parentTaskId
    )).jsonObject
}

