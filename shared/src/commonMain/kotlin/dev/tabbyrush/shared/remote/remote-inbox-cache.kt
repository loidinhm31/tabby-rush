package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.contract.RemoteInboxPage
import kotlinx.datetime.Instant
import kotlin.time.TimeSource

interface MonotonicClock {
    fun markNow(): Long
}

class SystemMonotonicClock : MonotonicClock {
    private val start = TimeSource.Monotonic.markNow()
    override fun markNow(): Long = start.elapsedNow().inWholeMilliseconds
}

class RemoteInboxCache(
    val clock: MonotonicClock = SystemMonotonicClock()
) {
    companion object {
        const val MAX_CACHE_ITEMS = 200
    }

    private var itemsList = mutableListOf<RemoteInboxItem>()
    private var nextCursorValue: String? = null
    private var serverNowAnchorMs: Long? = null
    private var monotonicAnchorMs: Long? = null

    val nextCursor: String?
        get() = nextCursorValue

    fun updateServerNow(serverNowIso: String) {
        try {
            val parsed = Instant.parse(serverNowIso).toEpochMilliseconds()
            val now = clock.markNow()
            val currentEstimate = currentServerTimeMs(now)
            // Prevent stale responses from moving estimated server time backward
            if (currentEstimate == null || parsed >= currentEstimate) {
                serverNowAnchorMs = parsed
                monotonicAnchorMs = now
            }
        } catch (_: Exception) {
            // Invalid ISO string ignored
        }
    }

    fun currentServerTimeMs(now: Long = clock.markNow()): Long? {
        val sAnchor = serverNowAnchorMs ?: return null
        val mAnchor = monotonicAnchorMs ?: return null
        val elapsed = now - mAnchor
        return sAnchor + (if (elapsed >= 0) elapsed else 0L)
    }

    fun isExpired(expiresAtIso: String, nowMs: Long? = currentServerTimeMs()): Boolean {
        if (nowMs == null) return false
        return try {
            val expiresEpochMs = Instant.parse(expiresAtIso).toEpochMilliseconds()
            expiresEpochMs <= nowMs
        } catch (_: Exception) {
            false
        }
    }

    fun replaceFirstPage(page: RemoteInboxPage) {
        updateServerNow(page.serverNow)
        nextCursorValue = page.nextCursor

        val validItems = page.items.filter { !isExpired(it.expiresAt) }
        val merged = mutableListOf<RemoteInboxItem>()
        for (newItem in validItems) {
            merged.add(newItem)
        }
        itemsList = if (merged.size > MAX_CACHE_ITEMS) {
            merged.take(MAX_CACHE_ITEMS).toMutableList()
        } else {
            merged
        }
    }

    fun appendPage(page: RemoteInboxPage) {
        updateServerNow(page.serverNow)
        nextCursorValue = page.nextCursor

        val newValidItems = page.items.filter { !isExpired(it.expiresAt) }
        val itemMap = itemsList.associateBy { it.id }.toMutableMap()

        for (item in newValidItems) {
            val existing = itemMap[item.id]
            if (existing != null) {
                itemMap[item.id] = mergeItem(existing, item)
            } else {
                itemMap[item.id] = item
            }
        }

        val sorted = itemMap.values.sortedWith(
            compareByDescending<RemoteInboxItem> { it.createdAt }
                .thenByDescending { it.id }
        )

        // Sliding page window: evict off-screen front data if size exceeds MAX_CACHE_ITEMS
        itemsList = if (sorted.size > MAX_CACHE_ITEMS) {
            sorted.drop(sorted.size - MAX_CACHE_ITEMS).toMutableList()
        } else {
            sorted.toMutableList()
        }
    }

    fun upsertItem(item: RemoteInboxItem) {
        if (isExpired(item.expiresAt)) {
            itemsList.removeAll { it.id == item.id }
            return
        }

        val index = itemsList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val existing = itemsList[index]
            itemsList[index] = mergeItem(existing, item)
        } else {
            itemsList.add(item)
            itemsList.sortWith(
                compareByDescending<RemoteInboxItem> { it.createdAt }
                    .thenByDescending { it.id }
            )
            if (itemsList.size > MAX_CACHE_ITEMS) {
                itemsList = itemsList.take(MAX_CACHE_ITEMS).toMutableList()
            }
        }
    }

    fun markItemRead(id: String, readAt: String): RemoteInboxItem? {
        val index = itemsList.indexOfFirst { it.id == id }
        if (index == -1) return null

        val existing = itemsList[index]
        val updatedReadAt = canonicalMonotonicReadAt(existing.readAt, readAt)
        val updated = existing.copy(readAt = updatedReadAt)
        itemsList[index] = updated
        return updated
    }

    fun getItem(id: String): RemoteInboxItem? {
        val now = currentServerTimeMs()
        val item = itemsList.firstOrNull { it.id == id } ?: return null
        return if (isExpired(item.expiresAt, now)) null else item
    }

    fun getItems(): List<RemoteInboxItem> {
        val now = currentServerTimeMs()
        // If trustworthy anchor unavailable, hide until refresh
        if (now == null && serverNowAnchorMs == null) {
            return emptyList()
        }
        // Filter out expired items
        itemsList.removeAll { isExpired(it.expiresAt, now) }
        return itemsList.toList()
    }

    fun clear() {
        itemsList.clear()
        nextCursorValue = null
        serverNowAnchorMs = null
        monotonicAnchorMs = null
    }

    private fun mergeItem(existing: RemoteInboxItem, incoming: RemoteInboxItem): RemoteInboxItem {
        val mergedReadAt = canonicalMonotonicReadAt(existing.readAt, incoming.readAt)
        return incoming.copy(readAt = mergedReadAt)
    }

    private fun canonicalMonotonicReadAt(existing: String?, incoming: String?): String? {
        if (existing == null) return incoming
        if (incoming == null) return existing
        return try {
            val existingMs = Instant.parse(existing).toEpochMilliseconds()
            val incomingMs = Instant.parse(incoming).toEpochMilliseconds()
            if (existingMs <= incomingMs) existing else incoming
        } catch (_: Exception) {
            existing
        }
    }
}
