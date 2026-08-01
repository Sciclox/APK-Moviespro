package com.animetv.app.data.repository

import android.content.Context
import com.animetv.app.data.model.AnimeItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HomeCacheManager {
    private const val PREFS_NAME = "anime_home_cache"
    private const val KEY_HERO = "home_hero_list"
    private const val KEY_CATALOG = "home_catalog_list"
    private const val KEY_RELEASING = "home_releasing_list"
    private const val KEY_MOVIES_HERO = "movies_hero_list"

    fun getMoviesHeroList(context: Context): List<AnimeItem> {
        return getList(context, KEY_MOVIES_HERO)
    }

    fun saveMoviesHeroData(context: Context, hero: List<AnimeItem>?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!hero.isNullOrEmpty()) {
            prefs.edit().putString(KEY_MOVIES_HERO, gson.toJson(hero)).apply()
        }
    }

    private val gson = Gson()

    fun getHeroList(context: Context): List<AnimeItem> {
        return getList(context, KEY_HERO)
    }

    fun getCatalogList(context: Context): List<AnimeItem> {
        return getList(context, KEY_CATALOG)
    }

    fun getReleasingList(context: Context): List<AnimeItem> {
        return getList(context, KEY_RELEASING)
    }

    fun saveHomeData(
        context: Context,
        hero: List<AnimeItem>?,
        catalog: List<AnimeItem>?,
        releasing: List<AnimeItem>?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!hero.isNullOrEmpty()) {
            editor.putString(KEY_HERO, gson.toJson(hero))
        }
        if (!catalog.isNullOrEmpty()) {
            editor.putString(KEY_CATALOG, gson.toJson(catalog))
        }
        if (!releasing.isNullOrEmpty()) {
            editor.putString(KEY_RELEASING, gson.toJson(releasing))
        }
        editor.apply()
    }

    private fun getList(context: Context, key: String): List<AnimeItem> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(key, null) ?: return emptyList()
            val type = object : TypeToken<List<AnimeItem>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
}
