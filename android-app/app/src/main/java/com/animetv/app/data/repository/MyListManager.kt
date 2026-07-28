package com.animetv.app.data.repository

import android.content.Context
import com.animetv.app.data.model.AnimeItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MyListManager {
    private const val PREFS_NAME = "animetv_mylist_prefs"
    private const val KEY_MY_LIST = "my_list_json"
    private val gson = Gson()

    /**
     * Get all saved animes from SharedPreferences
     */
    fun getSavedAnimes(context: Context): List<AnimeItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MY_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AnimeItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Check if an anime is in My List by ID or slug
     */
    fun isInList(context: Context, animeId: String): Boolean {
        val list = getSavedAnimes(context)
        return list.any { it.id == animeId || it.slug == animeId }
    }

    /**
     * Toggle an anime in My List (adds if not present, removes if present)
     * Returns true if added, false if removed
     */
    fun toggleAnime(context: Context, anime: AnimeItem): Boolean {
        val currentList = getSavedAnimes(context).toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == anime.id || it.slug == anime.slug }

        val isAdded: Boolean
        if (existingIndex >= 0) {
            currentList.removeAt(existingIndex)
            isAdded = false
        } else {
            currentList.add(0, anime) // Add to top of list
            isAdded = true
        }

        saveList(context, currentList)
        return isAdded
    }

    private fun saveList(context: Context, list: List<AnimeItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_MY_LIST, json).apply()
    }
}
