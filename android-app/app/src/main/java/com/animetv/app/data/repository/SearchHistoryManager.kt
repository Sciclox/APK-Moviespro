package com.animetv.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SearchHistoryManager {
    private const val PREFS_NAME = "anime_search_history"
    private const val KEY_HISTORY = "recent_queries"
    private const val MAX_HISTORY = 6

    private val gson = Gson()

    fun getHistory(context: Context): List<String> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    fun addQuery(context: Context, query: String) {
        val q = query.trim()
        if (q.isBlank() || q.length < 2) return
        val current = getHistory(context).toMutableList()
        current.remove(q)
        current.add(0, q)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.lastIndex)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(current)).apply()
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
