package com.animetv.app.data.repository

import android.content.Context
import com.animetv.app.data.model.AnimeItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

object CatalogCacheManager {
    private const val CACHE_EXPIRATION_MS = 30 * 60 * 1000L // 30 minutes cache
    private const val PREFS_NAME = "catalog_genre_disk_cache"

    private data class CacheEntry(
        val timestamp: Long,
        val items: List<AnimeItem>
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val gson = Gson()

    fun get(context: Context, key: String): List<AnimeItem>? {
        // 1. Check in-memory cache first
        val memoryEntry = cache[key]
        if (memoryEntry != null && (System.currentTimeMillis() - memoryEntry.timestamp <= CACHE_EXPIRATION_MS)) {
            return memoryEntry.items
        }

        // 2. Fallback to SharedPreferences disk cache for instant row rendering
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(key, null)
            val time = prefs.getLong("${key}_time", 0L)

            if (json != null && (System.currentTimeMillis() - time <= CACHE_EXPIRATION_MS)) {
                val type = object : TypeToken<List<AnimeItem>>() {}.type
                val items: List<AnimeItem>? = gson.fromJson(json, type)
                if (!items.isNullOrEmpty()) {
                    cache[key] = CacheEntry(time, items)
                    return items
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun get(key: String): List<AnimeItem>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRATION_MS) {
            cache.remove(key)
            return null
        }
        return entry.items
    }

    fun put(context: Context, key: String, items: List<AnimeItem>) {
        if (items.isNotEmpty()) {
            val now = System.currentTimeMillis()
            cache[key] = CacheEntry(now, items)

            // Save to disk asynchronously
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(key, gson.toJson(items))
                    .putLong("${key}_time", now)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun put(key: String, items: List<AnimeItem>) {
        if (items.isNotEmpty()) {
            cache[key] = CacheEntry(System.currentTimeMillis(), items)
        }
    }

    fun clear(context: Context) {
        cache.clear()
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
    }
}
