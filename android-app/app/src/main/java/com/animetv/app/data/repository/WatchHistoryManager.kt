package com.animetv.app.data.repository

import android.content.Context
import com.animetv.app.data.model.WatchHistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WatchHistoryManager {
    private const val PREFS_NAME = "animetv_watch_history_prefs"
    private const val KEY_HISTORY_LIST = "history_list_json"
    private val gson = Gson()

    /**
     * Get all watch history items ordered by most recently updated first
     */
    fun getHistory(context: Context): List<WatchHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchHistoryItem>>() {}.type
            val list: List<WatchHistoryItem> = gson.fromJson(json, type) ?: emptyList()
            list.map { item ->
                val cleanImg = if (item.animeImage.isBlank() || item.animeImage.contains("animeav1")) {
                    "https://cdn.jkdesa.com/assets/images/animes/image/${item.animeSlug}.jpg"
                } else {
                    item.animeImage
                }
                item.copy(animeImage = cleanImg)
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Save or update watch progress for a specific anime & episode
     */
    fun saveWatchProgress(
        context: Context,
        animeId: String,
        animeSlug: String,
        animeTitle: String,
        animeImage: String,
        backdrop: String?,
        episodeNumber: Int,
        episodeTitle: String,
        lastPositionMs: Long,
        durationMs: Long
    ) {
        if (animeSlug.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val currentList = getHistory(context).toMutableList()
            currentList.removeAll { it.animeSlug == animeSlug || it.animeId == animeId }

            val newItem = WatchHistoryItem(
                animeId = animeId,
                animeSlug = animeSlug,
                animeTitle = animeTitle,
                animeImage = animeImage,
                backdrop = backdrop,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                lastPositionMs = lastPositionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )

            currentList.add(0, newItem)
            saveList(context, currentList)

            // Sync with Android TV / Google TV system 'Seguir viendo' (Watch Next) row
            TvWatchNextManager.updateWatchNext(
                context = context,
                animeId = animeId,
                animeSlug = animeSlug,
                animeTitle = animeTitle,
                animeImage = animeImage,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                lastPositionMs = lastPositionMs,
                durationMs = durationMs
            )
        }
    }

    /**
     * Remove an item from watch history
     */
    fun removeHistoryItem(context: Context, animeSlug: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentList = getHistory(context).toMutableList()
            currentList.removeAll { it.animeSlug == animeSlug }
            saveList(context, currentList)
        }
    }

    private fun saveList(context: Context, list: List<WatchHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list.take(30)) // Keep top 30 recently watched
        prefs.edit().putString(KEY_HISTORY_LIST, json).apply()
    }
}
