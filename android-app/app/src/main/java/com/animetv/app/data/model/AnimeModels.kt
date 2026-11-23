package com.animetv.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

// ==========================================
// ZONA API MODELS
// ==========================================

@Immutable
data class ZonaPagination(
    @SerializedName("next") val next: String? = null,
    @SerializedName("prev") val prev: String? = null,
    @SerializedName("first") val first: String? = null,
    @SerializedName("last") val last: String? = null
)

@Immutable
data class ZonaMovieItem(
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("image") val image: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("extractUrl") val extractUrl: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("languages") val languages: List<String>? = null
)

@Immutable
data class ZonaListResponse(
    @SerializedName("currentPage") val currentPage: Int = 1,
    @SerializedName("totalPages") val totalPages: Int = 1,
    @SerializedName("remainingPages") val remainingPages: Int = 0,
    @SerializedName("featured") val featured: List<ZonaMovieItem> = emptyList(),
    @SerializedName("movies") val movies: List<ZonaMovieItem> = emptyList(),
    @SerializedName("pagination") val pagination: ZonaPagination? = null
)

@Immutable
data class ZonaStream(
    @SerializedName("type") val type: String = "hls",
    @SerializedName("url") val url: String,
    @SerializedName("source") val source: String? = null,
    @SerializedName("foundVia") val foundVia: String? = null,
    @SerializedName("resolvedThrough") val resolvedThrough: String? = null
)

@Immutable
data class ZonaExtractResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("post_id") val postId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("tmdbRating") val tmdbRating: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("genres") val genres: List<String> = emptyList(),
    @SerializedName("gallery") val gallery: List<String> = emptyList(),
    @SerializedName("streams") val streams: List<ZonaStream> = emptyList(),
    @SerializedName("streamsCount") val streamsCount: Int = 0
)

// ==========================================
// MAPPING EXTENSIONS
// ==========================================

fun String.unescapeHtml(): String {
    return this.replace("&#8211;", "-")
        .replace("&#038;", "&")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#8217;", "'")
        .replace("&#8216;", "'")
}

fun ZonaMovieItem.toAnimeItem(): AnimeItem {
    val cleanTitle = title.unescapeHtml()
    val parsedScore = rating?.replace("N/A", "")?.trim()?.toDoubleOrNull()
    return AnimeItem(
        id = url,
        title = cleanTitle,
        slug = url,
        image = image,
        backdrop = image,
        type = if (!quality.isNullOrBlank()) "Película • $quality" else "Película",
        status = year ?: "Estreno",
        synopsis = if (!languages.isNullOrEmpty()) "Idiomas: ${languages.joinToString(", ")}" else null
    )
}

fun ZonaExtractResponse.toAnimeDetail(movieUrl: String): AnimeDetail {
    val cleanTitle = title.unescapeHtml()
    val parsedScore = (rating ?: tmdbRating)?.replace("N/A", "")?.trim()?.toDoubleOrNull()
    val primaryBackdrop = gallery.firstOrNull() ?: poster ?: ""
    val movieEpisode = EpisodeItem(
        id = movieUrl,
        number = 1,
        title = "Película Completa",
        image = primaryBackdrop,
        duration = "Película",
        overview = description,
        slug = movieUrl
    )

    return AnimeDetail(
        id = movieUrl,
        title = cleanTitle,
        englishTitle = null,
        japaneseTitle = null,
        slug = movieUrl,
        synopsis = description ?: "Sin descripción disponible.",
        image = poster ?: primaryBackdrop,
        backdrop = primaryBackdrop,
        genres = genres,
        status = "Película",
        score = parsedScore,
        year = null,
        type = "Película",
        episodes = listOf(movieEpisode),
        relatedAnime = null,
        recommendedAnime = null
    )
}

// ==========================================
// LEGACY & UI COMPATIBLE MODELS
// ==========================================

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

