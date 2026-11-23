package com.animetv.app.data.repository

import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val message: String) : RepositoryResult<Nothing>
}

/** Single access point for remote catalog data. Composables must not call Retrofit directly. */
object AnimeRepository {
    private val allLoadedMovies = CopyOnWriteArrayList<AnimeItem>()
    private val movieDetailsCache = ConcurrentHashMap<String, AnimeDetail>()
    private val movieExtractCache = ConcurrentHashMap<String, ZonaExtractResponse>()
    private var isPreloadingStarted = false

    suspend fun getHeroMovies(): RepositoryResult<List<AnimeItem>> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.getList(page = 1)
            val heroItems = response.featured.map { it.toAnimeItem() }
            val normalItems = response.movies.map { it.toAnimeItem() }
            
            heroItems.forEach { addMovieIfAbsent(it) }
            normalItems.forEach { addMovieIfAbsent(it) }

            RepositoryResult.Success(heroItems.ifEmpty { normalItems.take(5) })
        } catch (e: Exception) {
            RepositoryResult.Failure(e.message ?: "Error al obtener películas destacadas")
        }
    }

    suspend fun getMoviesPage(page: Int = 1): RepositoryResult<List<AnimeItem>> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.getList(page = page)
            val items = response.movies.map { it.toAnimeItem() }
            items.forEach { addMovieIfAbsent(it) }
            RepositoryResult.Success(items)
        } catch (e: Exception) {
            RepositoryResult.Failure(e.message ?: "Error al obtener catálogo de películas")
        }
    }

    suspend fun getMovieDetails(movieUrl: String): RepositoryResult<AnimeDetail> = withContext(Dispatchers.IO) {
        try {
            movieDetailsCache[movieUrl]?.let { return@withContext RepositoryResult.Success(it) }

            if (movieUrl.contains("tudorama.com")) {
                val detail = fetchTudoramaDetails(movieUrl)
                if (detail != null) {
                    movieDetailsCache[movieUrl] = detail
                    return@withContext RepositoryResult.Success(detail)
                }
            }

            if (movieUrl.contains("/tvshows/")) {
                val detail = fetchSeriesDetailsFromWeb(movieUrl)
                if (detail != null) {
                    movieDetailsCache[movieUrl] = detail
                    return@withContext RepositoryResult.Success(detail)
                }
            }

            val response = ApiClient.apiService.extractMovie(movieUrl)
            val detail = response.toAnimeDetail(movieUrl)
            movieDetailsCache[movieUrl] = detail
            RepositoryResult.Success(detail)
        } catch (e: Exception) {
            RepositoryResult.Failure(e.message ?: "Error al extraer información de la película")
        }
    }

    suspend fun extractStream(movieUrl: String): RepositoryResult<ZonaExtractResponse> = withContext(Dispatchers.IO) {
        try {
            movieExtractCache[movieUrl]?.let { return@withContext RepositoryResult.Success(it) }

            // Step 1: Real-time native extractor (resolves fresh MovieDays tokens and direct CDNs)
            val directStreams: List<ZonaStream> = resolveStreamsDirectly(movieUrl)

            // Step 2: Fallback to ZonaAPI
            val apiStreams: List<ZonaStream> = try {
                val response = ApiClient.apiService.extractMovie(movieUrl)
                response.streams.map { s ->
                    var raw = s.url
                    if (raw.contains("url=")) {
                        val encodedParam = raw.substringAfter("url=").substringBefore("&")
                        try {
                            val decoded = String(android.util.Base64.decode(encodedParam, android.util.Base64.DEFAULT), Charsets.UTF_8)
                            if (decoded.startsWith("http")) raw = decoded
                        } catch (_: Exception) {}
                    }
                    ZonaStream(
                        url = raw,
                        type = if (raw.contains(".m3u8")) "hls" else s.type,
                        source = s.source ?: "Servidor Alternativo"
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }

            val allStreams = (directStreams + apiStreams).distinctBy { it.url }.sortedByDescending { s ->
                when {
                    s.source?.contains("MovieDays", ignoreCase = true) == true -> 10
                    s.url.contains("moviedays", ignoreCase = true) || s.url.contains("11231data") -> 9
                    s.url.contains(".m3u8") -> 8
                    s.url.contains(".aaa.mp4") || s.url.contains(".caa.mp4") -> 7
                    s.url.contains("1a-1791.com") -> 5
                    else -> 1
                }
            }

            if (allStreams.isNotEmpty()) {
                val finalResponse = ZonaExtractResponse(
                    title = "Reproducción HD",
                    rating = null,
                    tmdbRating = null,
                    description = null,
                    poster = null,
                    gallery = emptyList(),
                    genres = emptyList(),
                    streamsCount = allStreams.size,
                    streams = allStreams
                )
                movieExtractCache[movieUrl] = finalResponse
                RepositoryResult.Success(finalResponse)
            } else {
                RepositoryResult.Failure("No se encontraron fuentes de video disponibles.")
            }
        } catch (e: Exception) {
            RepositoryResult.Failure(e.message ?: "Error al extraer stream de la película")
        }
    }

    suspend fun getCategoryHub(category: String): RepositoryResult<CategoryHubResponse> = withContext(Dispatchers.IO) {
        try {
            val p1 = ApiClient.apiService.getList(page = 1)
            val p2 = try { ApiClient.apiService.getList(page = 2) } catch (_: Exception) { null }
            val p3 = try { ApiClient.apiService.getList(page = 3) } catch (_: Exception) { null }
            val p4 = try { ApiClient.apiService.getList(page = 4) } catch (_: Exception) { null }

            val rawFeatured = p1.featured.map { it.toAnimeItem() }
            val rawMovies = (p1.movies + (p2?.movies ?: emptyList()) + (p3?.movies ?: emptyList()) + (p4?.movies ?: emptyList())).map { it.toAnimeItem() }

            (rawFeatured + rawMovies).forEach { addMovieIfAbsent(it) }

            val all = (rawFeatured + rawMovies).distinctBy { it.id }

            when (category.lowercase()) {
                "series" -> {
                    val scrapedSeries = fetchTvShowsFromWeb(1)
                    val scrapedKdramas = fetchKdramasFromWeb(1)
                    val seriesItems = if (scrapedSeries.isNotEmpty()) {
                        scrapedSeries
                    } else {
                        all.filter { item ->
                            val t = item.title.lowercase()
                            t.contains("temporada") || t.contains("season") || t.contains("serie")
                        }.ifEmpty { all.take(20) }
                    }

                    seriesItems.forEach { addMovieIfAbsent(it) }
                    scrapedKdramas.forEach { addMovieIfAbsent(it) }

                    val heroSeries = (scrapedKdramas.take(2) + seriesItems.take(4)).distinctBy { it.id }

                    val accion = seriesItems.filter { it.title.containsAny("gatillo", "peaky", "blade", "titan", "hunter", "bleach", "demon", "misión", "solo", "kaiju", "espadachín", "knife", "redención") }
                        .ifEmpty { seriesItems.shuffled().take(15) }

                    val kdramasSection = scrapedKdramas.ifEmpty {
                        seriesItems.filter { it.title.containsAny("kim", "corea", "dramas", "sabuesos", "kitty", "novio", "amor", "chica") }
                    }

                    val anime = seriesItems.filter { it.title.containsAny("death note", "immortal", "hajimete", "anime", "dragon", "naruto", "one piece", "demon slayer", "bleach", "katainaka", "gumball") }
                        .ifEmpty { seriesItems.take(15) }

                    val drama = seriesItems.filter { it.title.containsAny("peaky", "blinders", "instituto", "beyond", "knife", "karma", "drama", "traición", "redención", "misterio", "dark") }
                        .ifEmpty { seriesItems.shuffled().take(15) }

                    val comedia = seriesItems.filter { it.title.containsAny("gumball", "walter", "chicos", "comedia", "comedy", "amigos", "familia", "fiesta", "daily life") }
                        .ifEmpty { seriesItems.shuffled().take(15) }

                    val sections = listOf(
                        CategoryHubSection("series_populares", "Series Populares & Tendencias", seriesItems.take(15)),
                        CategoryHubSection("series_kdramas", "K-Dramas & Doramas Coreanos", kdramasSection.take(15)),
                        CategoryHubSection("series_accion", "Series de Acción & Suspenso", accion.take(15)),
                        CategoryHubSection("series_anime", "Series de Animación & Anime", anime.take(15)),
                        CategoryHubSection("series_drama", "Series de Drama & Misterio", drama.take(15)),
                        CategoryHubSection("series_comedia", "Series de Comedia & Familia", comedia.take(15)),
                        CategoryHubSection("series_todas", "Catálogo Completo de Series", seriesItems.drop(15).take(25))
                    )

                    RepositoryResult.Success(CategoryHubResponse(hero = heroSeries, sections = sections))
                }
                "kdramas", "k-dramas" -> {
                    val tudoramaP1 = fetchTudoramaKdramas(1)
                    val tudoramaP2 = fetchTudoramaKdramas(2)
                    val tudoramaList = (tudoramaP1 + tudoramaP2).distinctBy { it.id }
                    tudoramaList.forEach { addMovieIfAbsent(it) }

                    val hero = tudoramaList.take(5)
                    val tendencias = tudoramaList.take(15)
                    val romance = tudoramaList.filter { it.title.containsAny("amor", "novio", "kitty", "chicos", "ojos", "besos", "vida", "esplendor", "acaramelados", "phobia", "love") }
                        .ifEmpty { tudoramaList.take(15) }
                    val accion = tudoramaList.filter { it.title.containsAny("driver", "taxi", "flex", "cop", "recruit", "justiciera", "killer", "mousetrap", "shin", "korea") }
                        .ifEmpty { tudoramaList.shuffled().take(15) }
                    val populares = tudoramaList.filter { it.title.containsAny("promesa", "royal", "hidden", "palacio", "east", "fila", "genio") }
                        .ifEmpty { tudoramaList.shuffled().take(15) }

                    val sections = listOf(
                        CategoryHubSection("kdrama_tendencias", "K-Dramas & Doramas en Tendencia", tendencias),
                        CategoryHubSection("kdrama_romance", "Romance & Sentimientos", romance),
                        CategoryHubSection("kdrama_accion", "Acción & Suspenso Coreano", accion),
                        CategoryHubSection("kdrama_populares", "Doramas Éxitos Mundiales", populares),
                        CategoryHubSection("kdrama_todos", "Catálogo Completo de TuDorama", tudoramaList.drop(15))
                    )
                    RepositoryResult.Success(CategoryHubResponse(hero = hero, sections = sections))
                }
                "peliculas" -> {
                    val moviesItems = all.filterNot { item ->
                        val t = item.title.lowercase()
                        t.contains("temporada") || t.contains("season")
                    }.distinctBy { it.id }

                    val heroMovies = rawFeatured.take(5).ifEmpty { moviesItems.take(5) }

                    val accion = moviesItems.filter { it.title.containsAny("thor", "robin hood", "acción", "action", "misión", "guerra", "terror", "911", "camino") }
                        .ifEmpty { moviesItems.shuffled().take(12) }

                    val animacion = moviesItems.filter { it.title.containsAny("rugrats", "no game no life", "animación", "animation") }
                        .ifEmpty { moviesItems.shuffled().take(12) }

                    val scifi = moviesItems.filter { it.title.containsAny("regreso al futuro", "futuro", "ciencia", "espacio") }
                        .ifEmpty { moviesItems.shuffled().take(12) }

                    val sections = listOf(
                        CategoryHubSection("pelis_populares", "Películas Populares & Estrenos", moviesItems.take(15)),
                        CategoryHubSection("pelis_accion", "Películas de Acción & Aventura", accion.take(15)),
                        CategoryHubSection("pelis_animacion", "Películas de Animación", animacion.take(15)),
                        CategoryHubSection("pelis_scifi", "Películas de Ciencia Ficción", scifi.take(15)),
                        CategoryHubSection("pelis_recientes", "Añadidas Recientemente", moviesItems.reversed().take(15))
                    )

                    RepositoryResult.Success(CategoryHubResponse(hero = heroMovies, sections = sections))
                }
                "tv" -> {
                    val liveTvChannels = listOf(
                        AnimeItem(
                            id = "tv-dw-espanol",
                            title = "DW Español HD",
                            image = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=500",
                            backdrop = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=1280",
                            synopsis = "Canal de noticias internacionales, reportajes y documentales en vivo las 24 horas en español.",
                            status = "En Vivo",
                            type = "tv_live",
                            slug = "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8"
                        ),
                        AnimeItem(
                            id = "tv-redbull",
                            title = "Red Bull TV",
                            image = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=500",
                            backdrop = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=1280",
                            synopsis = "Deportes extremos, eventos mundiales de acción, música en vivo y competiciones internacionales.",
                            status = "En Vivo",
                            type = "tv_live",
                            slug = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"
                        ),
                        AnimeItem(
                            id = "tv-nasa-hd",
                            title = "NASA TV HD",
                            image = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500",
                            backdrop = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1280",
                            synopsis = "Transmisiones en directo desde la Estación Espacial Internacional, misiones espaciales y vistas de la Tierra.",
                            status = "En Vivo",
                            type = "tv_live",
                            slug = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"
                        ),
                        AnimeItem(
                            id = "tv-aljazeera",
                            title = "Al Jazeera Noticias",
                            image = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=500",
                            backdrop = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=1280",
                            synopsis = "Señal de noticias globales de última hora, debates y análisis geopolítico las 24 horas.",
                            status = "En Vivo",
                            type = "tv_live",
                            slug = "https://live-hls-web-aja.getaj.net/AJA/index.m3u8"
                        )
                    )

                    val sections = listOf(
                        CategoryHubSection("tv_noticias", "Canales de Noticias & Información", listOf(liveTvChannels[0], liveTvChannels[3])),
                        CategoryHubSection("tv_deportes", "Deportes & Eventos en Vivo", listOf(liveTvChannels[1])),
                        CategoryHubSection("tv_ciencia", "Ciencia, Espacio & Documentales", listOf(liveTvChannels[2]))
                    )
                    RepositoryResult.Success(CategoryHubResponse(hero = liveTvChannels, sections = sections))
                }
                else -> {
                    val sections = listOf(
                        CategoryHubSection("recientes", "Novedades y Estrenos", all.take(12)),
                        CategoryHubSection("populares", "Más Populares", all.sortedByDescending { it.status }.take(12)),
                        CategoryHubSection("recomendadas", "Recomendadas para ti", all.reversed().take(12))
                    )
                    RepositoryResult.Success(CategoryHubResponse(hero = rawFeatured.take(5), sections = sections))
                }
            }
        } catch (e: Exception) {
            RepositoryResult.Failure(e.message ?: "Error al cargar sección $category")
        }
    }

    suspend fun preloadCatalog(maxPages: Int = 10) = withContext(Dispatchers.IO) {
        if (isPreloadingStarted) return@withContext
        isPreloadingStarted = true
        for (p in 1..maxPages) {
            try {
                val res = ApiClient.apiService.getList(page = p)
                res.featured.forEach { addMovieIfAbsent(it.toAnimeItem()) }
                res.movies.forEach { addMovieIfAbsent(it.toAnimeItem()) }
                if (res.currentPage >= res.totalPages) break
            } catch (_: Exception) {
                break
            }
        }
    }

    fun searchInMemory(query: String): List<AnimeItem> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return allLoadedMovies.toList()
        return allLoadedMovies.filter {
            it.title.lowercase().contains(q) ||
            (it.synopsis?.lowercase()?.contains(q) == true) ||
            (it.status?.lowercase()?.contains(q) == true)
        }.distinctBy { it.id }
    }

    fun getAllCachedMovies(): List<AnimeItem> = allLoadedMovies.toList()

    suspend fun getSeriesPage(page: Int = 1): RepositoryResult<List<AnimeItem>> = withContext(Dispatchers.IO) {
        try {
            val list = fetchTvShowsFromWeb(page)
            list.forEach { addMovieIfAbsent(it) }
            RepositoryResult.Success(list)
        } catch (e: Exception) {
            RepositoryResult.Failure(e.message ?: "Error al cargar catálogo de series")
        }
    }

    private fun fetchTvShowsFromWeb(page: Int): List<AnimeItem> {
        return try {
            val url = if (page <= 1) "https://zonaaps.com/genre/series-de-tv/" else "https://zonaaps.com/genre/series-de-tv/page/$page/"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://zonaaps.com/")
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string() ?: return emptyList()
            val articleRegex = Regex("""<article[^>]*id=["']post-(\d+)["'][^>]*>([\s\S]*?)</article>""", RegexOption.IGNORE_CASE)
            val linkRegex = Regex("""href=["'](https://zonaaps\.com/tvshows/[^"']+)["']""", RegexOption.IGNORE_CASE)
            val titleRegex = Regex("""<h3><a[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            val altRegex = Regex("""alt=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val imgRegex = Regex("""(?:data-lazy-src|src)=["'](https://[^"']+)["']""", RegexOption.IGNORE_CASE)
            val yearRegex = Regex("""<span>(\d{4})</span>""", RegexOption.IGNORE_CASE)

            val results = mutableListOf<AnimeItem>()
            for (m in articleRegex.findAll(html)) {
                val content = m.groupValues[2]
                val linkMatch = linkRegex.find(content)?.groupValues?.get(1)
                val titleMatch = titleRegex.find(content)?.groupValues?.get(1) ?: altRegex.find(content)?.groupValues?.get(1)
                val imgMatch = imgRegex.find(content)?.groupValues?.get(1)?.replace("/w185/", "/w780/")?.replace("/w300/", "/w780/")
                val yearMatch = yearRegex.find(content)?.groupValues?.get(1)

                if (!linkMatch.isNullOrBlank() && !titleMatch.isNullOrBlank()) {
                    val cleanTitle = titleMatch.unescapeHtml()
                    val posterUrl = imgMatch ?: ""
                    results.add(
                        AnimeItem(
                            id = linkMatch,
                            title = cleanTitle,
                            slug = linkMatch,
                            image = posterUrl,
                            backdrop = posterUrl,
                            type = "Serie",
                            status = yearMatch ?: "Serie TV",
                            synopsis = "Serie completa en HD disponible en Livmotion."
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchKdramasFromWeb(page: Int = 1): List<AnimeItem> {
        return try {
            val url = if (page <= 1) "https://zonaaps.com/genre/k-dramas/" else "https://zonaaps.com/genre/k-dramas/page/$page/"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://zonaaps.com/")
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string() ?: return emptyList()
            val articleRegex = Regex("""<article[^>]*id=["']post-(\d+)["'][^>]*>([\s\S]*?)</article>""", RegexOption.IGNORE_CASE)
            val linkRegex = Regex("""href=["'](https://zonaaps\.com/(?:tvshows|movies)/[^"']+)["']""", RegexOption.IGNORE_CASE)
            val titleRegex = Regex("""<h3><a[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            val altRegex = Regex("""alt=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val imgRegex = Regex("""(?:data-lazy-src|src)=["'](https://[^"']+)["']""", RegexOption.IGNORE_CASE)
            val yearRegex = Regex("""<span>(\d{4})</span>""", RegexOption.IGNORE_CASE)

            val results = mutableListOf<AnimeItem>()
            for (m in articleRegex.findAll(html)) {
                val content = m.groupValues[2]
                val linkMatch = linkRegex.find(content)?.groupValues?.get(1)
                val titleMatch = titleRegex.find(content)?.groupValues?.get(1) ?: altRegex.find(content)?.groupValues?.get(1)
                val imgMatch = imgRegex.find(content)?.groupValues?.get(1)?.replace("/w185/", "/w780/")?.replace("/w300/", "/w780/")
                val yearMatch = yearRegex.find(content)?.groupValues?.get(1)

                if (!linkMatch.isNullOrBlank() && !titleMatch.isNullOrBlank()) {
                    val cleanTitle = titleMatch.unescapeHtml()
                    val posterUrl = imgMatch ?: ""
                    results.add(
                        AnimeItem(
                            id = linkMatch,
                            title = cleanTitle,
                            slug = linkMatch,
                            image = posterUrl,
                            backdrop = posterUrl,
                            type = if (linkMatch.contains("/tvshows/")) "K-Drama" else "Película",
                            status = yearMatch ?: "K-Drama",
                            synopsis = "Drama coreano completo en HD disponible en Livmotion."
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchSeriesDetailsFromWeb(seriesUrl: String): AnimeDetail? {
        return try {
            val request = okhttp3.Request.Builder()
                .url(seriesUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://zonaaps.com/")
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null

            val titleMatch = Regex("""<h1[^>]*>([^<]+)</h1>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.unescapeHtml()
                ?: Regex("""<h2[^>]*>([^<]+)</h2>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.unescapeHtml()
                ?: "Serie"

            val synopsisMatch = Regex("""<div class=["']wp-content["'][^>]*><p>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""<div class=["']sinopsis["'][^>]*><p>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: "Disfruta de todos los capítulos completos en HD."

            val posterMatch = Regex("""(?:data-lazy-src|src)=["'](https://image\.tmdb\.org/t/p/[^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""(?:data-lazy-src|src)=["'](https://zonaaps\.com/tmdb-img/[^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""<div class=["']poster["'][^>]*><img[^>]*src=["'](https://[^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: ""
            val highResPoster = posterMatch.replace("/w185/", "/w780/").replace("/w300/", "/w780/")

            val ratingMatch = Regex("""class=["']rating["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.toDoubleOrNull()

            val genres = Regex("""<a href=["']https://zonaaps\.com/genre/[^"']+["'][^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].unescapeHtml() }.distinct().toList()

            // Parse episodes from <ul class="episodios">
            val episodes = mutableListOf<EpisodeItem>()
            val liRegex = Regex("""<li[^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE)
            val epLinkRegex = Regex("""href=['"](https://zonaaps\.com/episodes/[^'"]+)['"]""", RegexOption.IGNORE_CASE)
            val epTitleRegex = Regex("""<div class=['"]episodiotitle['"]><a[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            val epImgRegex = Regex("""(?:data-lazy-src|src)=['"](https://[^'"]+)['"]""", RegexOption.IGNORE_CASE)
            val epNumRegex = Regex("""<div class=['"]numerando['"]>(\d+)\s*-\s*(\d+)</div>""", RegexOption.IGNORE_CASE)

            var index = 1
            for (m in liRegex.findAll(html)) {
                val block = m.groupValues[1]
                val link = epLinkRegex.find(block)?.groupValues?.get(1) ?: continue
                val epTitle = epTitleRegex.find(block)?.groupValues?.get(1)?.unescapeHtml() ?: "Episodio $index"
                val epImg = epImgRegex.find(block)?.groupValues?.get(1)?.replace("/w185/", "/w780/") ?: highResPoster
                val numMatch = epNumRegex.find(block)
                val epNum = numMatch?.groupValues?.get(2)?.toIntOrNull() ?: index
                val seasonNum = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodes.add(
                    EpisodeItem(
                        id = link,
                        number = epNum,
                        title = if (seasonNum > 1) "T$seasonNum E$epNum - $epTitle" else epTitle,
                        image = epImg,
                        duration = "24 min",
                        overview = "Capítulo $epNum de la serie $titleMatch.",
                        slug = link
                    )
                )
                index++
            }

            val cleanSeriesSlug = seriesUrl.substringAfter("/tvshows/").trim('/')
            AnimeDetail(
                id = seriesUrl,
                title = titleMatch,
                englishTitle = null,
                japaneseTitle = null,
                slug = seriesUrl,
                synopsis = synopsisMatch.replace(Regex("<[^>]*>"), "").unescapeHtml(),
                image = highResPoster,
                backdrop = highResPoster,
                genres = genres.ifEmpty { listOf("Serie") },
                status = "Serie",
                score = ratingMatch,
                year = null,
                type = "Serie",
                episodes = episodes.ifEmpty {
                    List(12) { i ->
                        val epNumber = i + 1
                        val fallbackEpUrl = "https://zonaaps.com/episodes/$cleanSeriesSlug-1x$epNumber/"
                        EpisodeItem(
                            id = fallbackEpUrl,
                            number = epNumber,
                            title = "Episodio $epNumber",
                            image = posterMatch,
                            duration = "24 min",
                            overview = "Capítulo $epNumber de la serie $titleMatch.",
                            slug = fallbackEpUrl
                        )
                    }
                },
                relatedAnime = null,
                recommendedAnime = null
            )
        } catch (e: Exception) {
            null
        }
    }

    fun fetchTudoramaKdramas(page: Int = 1): List<AnimeItem> {
        return try {
            val urls = if (page <= 1) {
                listOf("https://tudorama.com/", "https://tudorama.com/genero/series/")
            } else {
                listOf("https://tudorama.com/genero/series/page/$page/")
            }

            val results = mutableListOf<AnimeItem>()
            val linkRegex = Regex("""<a[^>]*href=["'](https://tudorama\.com/serie/[^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
            val titleRegex = Regex("""<(?:h2|h3)[^>]*>([^<]+)</(?:h2|h3)>""", RegexOption.IGNORE_CASE)
            val altRegex = Regex("""alt=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val imgRegex = Regex("""(?:data-src|src)=["'](https://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""", RegexOption.IGNORE_CASE)

            for (u in urls) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(u)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://tudorama.com/")
                        .build()

                    val response = ApiClient.okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) continue
                    val html = response.body?.string() ?: continue

                    for (m in linkRegex.findAll(html)) {
                        val link = m.groupValues[1]
                        val inner = m.groupValues[2]
                        val titleMatch = titleRegex.find(inner)?.groupValues?.get(1) ?: altRegex.find(inner)?.groupValues?.get(1)
                        val imgMatch = imgRegex.find(inner)?.groupValues?.get(1)

                        if (!titleMatch.isNullOrBlank() && results.none { it.id == link }) {
                            val cleanTitle = titleMatch.unescapeHtml().trim()
                            val posterUrl = (imgMatch ?: "").replace("/w185/", "/w780/").replace("/w300/", "/w780/").replace("/w342/", "/w780/")
                            results.add(
                                AnimeItem(
                                    id = link,
                                    title = cleanTitle,
                                    slug = link,
                                    image = posterUrl,
                                    backdrop = posterUrl,
                                    type = "K-Drama",
                                    status = "TuDorama",
                                    synopsis = "Dorama coreano en HD disponible en TuDorama."
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchTudoramaDetails(seriesUrl: String): AnimeDetail? {
        return try {
            val request = okhttp3.Request.Builder()
                .url(seriesUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://tudorama.com/")
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null

            val titleMatch = Regex("""<h1[^>]*>([^<]+)</h1>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.unescapeHtml()
                ?: Regex("""<h2[^>]*>([^<]+)</h2>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.unescapeHtml()
                ?: "K-Drama"

            val synopsisMatch = Regex("""<div class=["']entry-content["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""<p class=["']description["'][^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: "Disfruta de todos los capítulos completos de este dorama en HD."

            val posterMatch = Regex("""(?:data-src|src)=["'](https://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: ""
            val highResPoster = posterMatch.replace("/w185/", "/w780/").replace("/w300/", "/w780/").replace("/w342/", "/w780/")

            // Parse episodes from href="https://tudorama.com/ver/<slug>/"
            val baseSlug = seriesUrl.substringAfter("/serie/").trim('/')
            val epRegex = Regex("""href=["'](https://tudorama\.com/ver/[^"']+)["']""", RegexOption.IGNORE_CASE)
            val foundEps = epRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()

            val maxEp = foundEps.mapNotNull { 
                Regex("""s\d+x(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() 
            }.maxOrNull() ?: foundEps.size.coerceAtLeast(16)

            val episodes = mutableListOf<EpisodeItem>()
            for (epNum in 1..maxEp) {
                val epLink = "https://tudorama.com/ver/$baseSlug-s1x$epNum/"
                episodes.add(
                    EpisodeItem(
                        id = epLink,
                        number = epNum,
                        title = "Episodio $epNum",
                        image = highResPoster,
                        duration = "45 min",
                        overview = "Capítulo $epNum del dorama $titleMatch.",
                        slug = epLink
                    )
                )
            }

            AnimeDetail(
                id = seriesUrl,
                title = titleMatch,
                englishTitle = null,
                japaneseTitle = null,
                slug = seriesUrl,
                synopsis = synopsisMatch.replace(Regex("<[^>]*>"), "").unescapeHtml(),
                image = highResPoster,
                backdrop = highResPoster,
                genres = listOf("K-Drama", "Dorama", "Romance", "Coreano"),
                status = "Dorama",
                score = 8.8,
                year = null,
                type = "K-Drama",
                episodes = episodes,
                relatedAnime = null,
                recommendedAnime = null
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveStreamsDirectly(pageUrl: String): List<ZonaStream> {
        val streams = mutableListOf<ZonaStream>()
        try {
            var targetPage = pageUrl
            if (targetPage.contains("/tvshows/")) {
                val slugName = targetPage.substringAfter("/tvshows/").trim('/')
                targetPage = "https://zonaaps.com/episodes/$slugName-1x1/"
            }

            val req = okhttp3.Request.Builder()
                .url(targetPage)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", if (targetPage.contains("tudorama.com")) "https://tudorama.com/" else "https://zonaaps.com/")
                .build()

            val res = ApiClient.okHttpClient.newCall(req).execute()
            if (!res.isSuccessful) return emptyList()
            val html = res.body?.string() ?: return emptyList()

            // 0. TuDorama servers
            if (targetPage.contains("tudorama.com")) {
                try {
                    val epId = Regex("""data-id=["'](\d+)["']""").find(html)?.groupValues?.get(1)
                        ?: Regex("""data-post=["'](\d+)["']""").find(html)?.groupValues?.get(1)
                    val nonce = Regex("""data-nonce=["']([^"']+)["']""").find(html)?.groupValues?.get(1)

                    if (!epId.isNullOrBlank()) {
                        val formBody = okhttp3.FormBody.Builder()
                            .add("action", "corvus_get_servers")
                            .add("post_id", epId)
                            .apply { if (!nonce.isNullOrBlank()) add("nonce", nonce) }
                            .build()

                        val ajaxReq = okhttp3.Request.Builder()
                            .url("https://tudorama.com/wp-admin/admin-ajax.php")
                            .post(formBody)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("Referer", targetPage)
                            .build()

                        val ajaxRes = ApiClient.okHttpClient.newCall(ajaxReq).execute()
                        if (ajaxRes.isSuccessful) {
                            val ajaxJson = ajaxRes.body?.string() ?: ""
                            val arr = org.json.JSONArray(ajaxJson)
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                val sUrl = item.optString("url")
                                val sName = item.optString("name", "Servidor HD")
                                if (sUrl.isNotBlank()) {
                                    streams.add(
                                        ZonaStream(
                                            url = sUrl,
                                            type = "embed",
                                            source = "TuDorama - $sName"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                val serverRegex = Regex("""href=["'](https://cdn\.tudorama\.com/server-(\d+)\.php\?id=([^"']+))["']""", RegexOption.IGNORE_CASE)
                for (m in serverRegex.findAll(html)) {
                    val cdnUrl = m.groupValues[1]
                    val serverType = m.groupValues[2]
                    val serverId = m.groupValues[3]

                    val embedUrl = when (serverType) {
                        "1" -> "https://hgcloud.to/e/$serverId"
                        "2" -> "https://filemoon.sx/e/$serverId"
                        "3" -> "https://earnvids.com/e/$serverId"
                        "4" -> "https://4meplayer.com/e/$serverId"
                        else -> cdnUrl
                    }

                    val serverLabel = when (serverType) {
                        "1" -> "StreamHG HD"
                        "2" -> "Filemoon HD"
                        "3" -> "EarnVids HD"
                        "4" -> "4MePlayer HD"
                        else -> "Servidor TuDorama HD"
                    }

                    if (streams.none { it.url == embedUrl }) {
                        streams.add(
                            ZonaStream(
                                url = embedUrl,
                                type = "embed",
                                source = serverLabel
                            )
                        )
                    }
                }
            }

            // 1. MovieDays embeds
            val moviedaysRegex = Regex("""src=["'](https://moviedays\.(?:top|lat|pro|is|site)/[^"']+)["']""", RegexOption.IGNORE_CASE)
            for (match in moviedaysRegex.findAll(html)) {
                val embedUrl = match.groupValues[1]
                try {
                    val embedReq = okhttp3.Request.Builder()
                        .url(embedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://zonaaps.com/")
                        .build()
                    val embedRes = ApiClient.okHttpClient.newCall(embedReq).execute()
                    if (embedRes.isSuccessful) {
                        val embedHtml = embedRes.body?.string() ?: ""
                        val configId = Regex("""const\s+configId\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(embedHtml)?.groupValues?.get(1)
                        val authToken = Regex("""const\s+AUTH_TOKEN\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(embedHtml)?.groupValues?.get(1)

                        if (!configId.isNullOrBlank() && !authToken.isNullOrBlank()) {
                            val hostUri = java.net.URI(embedUrl)
                            val origin = "${hostUri.scheme}://${hostUri.host}"
                            val apiUrl = "$origin/api/data.php?$configId"

                            val apiReq = okhttp3.Request.Builder()
                                .url(apiUrl)
                                .header("X-Auth-Token", authToken)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .header("Referer", embedUrl)
                                .build()

                            val apiRes = ApiClient.okHttpClient.newCall(apiReq).execute()
                            if (apiRes.isSuccessful) {
                                val apiBody = apiRes.body?.string() ?: ""
                                val json = org.json.JSONObject(apiBody)
                                val sourcesArr = json.optJSONArray("sources")
                                if (sourcesArr != null) {
                                    for (i in 0 until sourcesArr.length()) {
                                        val obj = sourcesArr.getJSONObject(i)
                                        val fileUrl = obj.optString("file")
                                        val typeStr = obj.optString("type", "hls")
                                        if (fileUrl.isNotBlank()) {
                                            streams.add(
                                                ZonaStream(
                                                    url = fileUrl,
                                                    type = typeStr,
                                                    source = "MovieDays HD"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. All series and anime embed iframes (series-embed2.php, embed2.php, anime-embed.php, anime-embed2.php, etc.)
            val iframesRegex = Regex("""<iframe[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            for (match in iframesRegex.findAll(html)) {
                val ifrUrl = match.groupValues[1]
                if (ifrUrl.contains("banner") || ifrUrl.contains("moviedays")) continue
                val fixedIfrUrl = ifrUrl.replace("zonaaps-player.xyz", "zonaaps.com")
                try {
                    val ifrReq = okhttp3.Request.Builder()
                        .url(fixedIfrUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://zonaaps.com/")
                        .build()
                    val ifrRes = ApiClient.okHttpClient.newCall(ifrReq).execute()
                    if (ifrRes.isSuccessful) {
                        val ifrHtml = ifrRes.body?.string() ?: ""
                        val mediaMatches = Regex("""(https://(?:[a-zA-Z0-9.-]+\.cdn\.rumble\.cloud|1a-1791\.com)/video/[^\s"'<>]+\.(?:aaa\.mp4|caa\.mp4|mp4|tar|m3u8)[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
                        for (m in mediaMatches.findAll(ifrHtml)) {
                            val mediaUrl = m.groupValues[1]
                            if (streams.none { it.url == mediaUrl }) {
                                streams.add(
                                    ZonaStream(
                                        url = mediaUrl,
                                        type = if (mediaUrl.contains(".tar") || mediaUrl.contains(".m3u8")) "hls" else "mp4",
                                        source = "Servidor Serie HD"
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Direct Rumble / 1a-1791 CDNs in page HTML
            val rumbleRegex = Regex("""(https://(?:[a-zA-Z0-9.-]+\.cdn\.rumble\.cloud|1a-1791\.com)/video/[^\s"']+\.(?:aaa\.mp4|caa\.mp4|mp4|tar|m3u8)[^\s"']*)""", RegexOption.IGNORE_CASE)
            for (match in rumbleRegex.findAll(html)) {
                val mediaUrl = match.groupValues[1]
                if (streams.none { it.url == mediaUrl }) {
                    streams.add(
                        ZonaStream(
                            url = mediaUrl,
                            type = if (mediaUrl.contains(".tar") || mediaUrl.contains(".m3u8")) "hls" else "mp4",
                            source = "Servidor CDN HD"
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return streams
    }

    private fun addMovieIfAbsent(item: AnimeItem) {
        if (allLoadedMovies.none { it.id == item.id }) {
            allLoadedMovies.add(item)
        }
    }
}

private fun String.containsAny(vararg keywords: String): Boolean {
    return keywords.any { this.contains(it, ignoreCase = true) }
}


