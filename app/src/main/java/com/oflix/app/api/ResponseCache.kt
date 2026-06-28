package com.oflix.app.api

import com.google.gson.JsonObject
import com.oflix.app.data.MediaItem

/**
 * In-memory cache with configurable TTL.
 * Used to avoid re-fetching home categories and detail data on every screen visit.
 */
object ResponseCache {
    private data class CacheEntry<T>(val data: T, val timestamp: Long)

    private val rankingCache = mutableMapOf<String, CacheEntry<List<MediaItem>>>()
    private val detailCache = mutableMapOf<String, CacheEntry<JsonObject>>()

    private const val RANKING_TTL = 60 * 60 * 1000L  // 1 hour
    private const val DETAIL_TTL = 60 * 60 * 1000L   // 1 hour

    fun getRanking(id: String): List<MediaItem>? {
        val entry = rankingCache[id] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > RANKING_TTL) {
            rankingCache.remove(id)
            return null
        }
        return entry.data
    }

    fun putRanking(id: String, items: List<MediaItem>) {
        rankingCache[id] = CacheEntry(items, System.currentTimeMillis())
    }

    fun getDetail(detailPath: String): JsonObject? {
        val entry = detailCache[detailPath] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > DETAIL_TTL) {
            detailCache.remove(detailPath)
            return null
        }
        return entry.data
    }

    fun putDetail(detailPath: String, data: JsonObject) {
        detailCache[detailPath] = CacheEntry(data, System.currentTimeMillis())
    }

    fun clearAll() {
        rankingCache.clear()
        detailCache.clear()
    }
}
