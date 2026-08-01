package com.animetv.app.data.repository

import com.animetv.app.data.model.AnimeItem
import java.util.concurrent.ConcurrentHashMap

object CatalogCacheManager {
    private const val CACHE_EXPIRATION_MS = 5 * 60 * 1000L // 5 minutes cache

    private data class CacheEntry(
        val timestamp: Long,
        val items: List<AnimeItem>
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(key: String): List<AnimeItem>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRATION_MS) {
            cache.remove(key)
            return null
        }
        return entry.items
    }

    fun put(key: String, items: List<AnimeItem>) {
        if (items.isNotEmpty()) {
            cache[key] = CacheEntry(System.currentTimeMillis(), items)
        }
    }

    fun clear() {
        cache.clear()
    }
}
