package com.animetv.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T
)

@Immutable
data class RecentEpisodeItem(
    @SerializedName("id") val id: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("title") val title: String,
    @SerializedName("episodeNumber") val episodeNumber: Int,
    @SerializedName("image") val image: String,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("synopsis") val synopsis: String? = null,
    @SerializedName("dateText") val dateText: String? = null
)

@Immutable
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

@Immutable
data class EpisodeItem(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: Int,
    @SerializedName("title") val title: String,
    @SerializedName("image") val image: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("slug") val slug: String
)

@Immutable
data class RelatedAnimeItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("relation") val relation: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("image") val image: String? = null
)

@Immutable
data class RecommendedAnimeItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("image") val image: String? = null
)

@Immutable
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
    @SerializedName("episodes") val episodes: List<EpisodeItem>,
    @SerializedName("relatedAnime") val relatedAnime: List<RelatedAnimeItem>? = null,
    @SerializedName("recommendedAnime") val recommendedAnime: List<RecommendedAnimeItem>? = null
)

@Immutable
data class StreamSource(
    @SerializedName("server") val server: String,
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("format") val format: String?,
    @SerializedName("audio") val audio: String?
)

@Immutable
data class WatchResponse(
    @SerializedName("animeTitle") val animeTitle: String,
    @SerializedName("episodeNumber") val episodeNumber: Int,
    @SerializedName("servers") val servers: List<StreamSource>
)

@Immutable
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

@Immutable
data class CategoryHubSection(
    @SerializedName("slug") val slug: String,
    @SerializedName("title") val title: String,
    @SerializedName("items") val items: List<AnimeItem>
)

@Immutable
data class CategoryHubResponse(
    @SerializedName("hero") val hero: List<AnimeItem>,
    @SerializedName("sections") val sections: List<CategoryHubSection>
)

