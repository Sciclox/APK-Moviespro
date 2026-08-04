package com.animetv.app.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T
)

data class RecentEpisodeItem(
    @SerializedName("id") val id: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("title") val title: String,
    @SerializedName("episodeNumber") val episodeNumber: Int,
    @SerializedName("image") val image: String,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("synopsis") val synopsis: String? = null
)

data class AnimeItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("image") val image: String,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("synopsis") val synopsis: String? = null
)

data class EpisodeItem(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: Int,
    @SerializedName("title") val title: String,
    @SerializedName("image") val image: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("slug") val slug: String
)

data class AnimeDetail(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("englishTitle") val englishTitle: String? = null,
    @SerializedName("japaneseTitle") val japaneseTitle: String?,
    @SerializedName("slug") val slug: String,
    @SerializedName("synopsis") val synopsis: String,
    @SerializedName("image") val image: String,
    @SerializedName("backdrop") val backdrop: String?,
    @SerializedName("genres") val genres: List<String>,
    @SerializedName("status") val status: String?,
    @SerializedName("score") val score: Double?,
    @SerializedName("year") val year: Int?,
    @SerializedName("type") val type: String?,
    @SerializedName("episodes") val episodes: List<EpisodeItem>
)

data class StreamSource(
    @SerializedName("server") val server: String,
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("format") val format: String?,
    @SerializedName("audio") val audio: String?
)

data class WatchResponse(
    @SerializedName("animeTitle") val animeTitle: String,
    @SerializedName("episodeNumber") val episodeNumber: Int,
    @SerializedName("servers") val servers: List<StreamSource>
)

data class WatchHistoryItem(
    @SerializedName("animeId") val animeId: String,
    @SerializedName("animeSlug") val animeSlug: String,
    @SerializedName("animeTitle") val animeTitle: String,
    @SerializedName("animeImage") val animeImage: String,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("episodeNumber") val episodeNumber: Int,
    @SerializedName("episodeTitle") val episodeTitle: String,
    @SerializedName("lastPositionMs") val lastPositionMs: Long = 0L,
    @SerializedName("durationMs") val durationMs: Long = 0L,
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
)
