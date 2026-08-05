package com.animetv.app.ui.screens

import android.graphics.drawable.BitmapDrawable
import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.repository.CatalogCacheManager
import com.animetv.app.data.repository.HomeCacheManager
import com.animetv.app.ui.components.NetflixLogo
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvHeroBanner
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.NetflixRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoviesScreen(
    isScreenActive: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val context = LocalContext.current
    var moviesPage by remember { mutableIntStateOf(1) }
    val cachedMoviesHero = remember { HomeCacheManager.getMoviesHeroList(context) }
    val moviesList = remember { mutableStateListOf<AnimeItem>().apply { addAll(cachedMoviesHero) } }
    var isLoadingMovies by remember { mutableStateOf(cachedMoviesHero.isEmpty()) }
    var hasMorePages by remember { mutableStateOf(true) }

    val cleanMoviesList = remember(moviesList.size) {
        moviesList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) }
    }

    // Focus restoration when returning to MoviesScreen
    LaunchedEffect(isScreenActive, lastSelectedAnime) {
        if (isScreenActive) {
            lastSelectedAnime?.let { anime ->
                if (cleanMoviesList.isNotEmpty() && anime.id == cleanMoviesList.first().id) {
                    delay(300)
                    try {
                        firstKeyFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var verticalScrollJob by remember { mutableStateOf<Job?>(null) }
    val scrollToRow: (Int) -> Unit = { targetIndex ->
        verticalScrollJob?.cancel()
        verticalScrollJob = scope.launch {
            delay(30)
            if (listState.firstVisibleItemIndex != targetIndex || listState.firstVisibleItemScrollOffset > 5) {
                listState.animateScrollToItem(targetIndex, 0)
            }
        }
    }

    val allSections = remember {
        listOf(
            "accion" to "Películas de Acción",
            "aventura" to "Películas de Aventura",
            "fantasia" to "Películas de Fantasía",
            "sci-fi" to "Películas de Ciencia Ficción",
            "romance" to "Películas de Romance",
            "comedia" to "Películas de Comedia",
            "drama" to "Películas de Drama",
            "sobrenatural" to "Películas Sobrenaturales",
            "shounen" to "Películas Shounen",
            "terror" to "Películas de Terror",
            "isekai" to "Películas Isekai",
            "mecha" to "Películas Mecha"
        )
    }

    // Load Movies & Genre Sections in Parallel (Synchronized Instant Loading)
    LaunchedEffect(Unit) {
        moviesPage = 1
        hasMorePages = true

        val cacheKey = "peliculas:all:1"
        val cachedMovies = CatalogCacheManager.get(cacheKey)

        if (cachedMovies != null && cachedMovies.isNotEmpty()) {
            moviesList.clear()
            moviesList.addAll(cachedMovies)
            isLoadingMovies = false
        } else {
            isLoadingMovies = true
        }

        try {
            coroutineScope {
                // 1. Fetch main catalog & pre-enrich top 5 hero movies with TMDB backdrops
                val mainJob = async(Dispatchers.IO) {
                    try {
                        val res = ApiClient.apiService.getCatalog(tipo = "peliculas", page = 1)
                        if (res.success && res.data.isNotEmpty()) {
                            val top5Enriched = res.data.take(5).map { item ->
                                async(Dispatchers.IO) {
                                    try {
                                        val details = ApiClient.apiService.getAnimeDetails(item.slug)
                                        if (details.success && !details.data.backdrop.isNullOrEmpty()) {
                                            return@async item.copy(backdrop = details.data.backdrop)
                                        }
                                    } catch (_: Exception) {}
                                    item
                                }
                            }.awaitAll()

                            val fullList = res.data.toMutableList()
                            top5Enriched.forEachIndexed { i, item -> fullList[i] = item }

                            withContext(Dispatchers.Main) {
                                moviesList.clear()
                                moviesList.addAll(fullList)
                            }
                            CatalogCacheManager.put(cacheKey, fullList)
                            HomeCacheManager.saveMoviesHeroData(context, fullList.take(5))
                        }
                    } catch (_: Exception) {}
                }

                // 3. Fetch popular movie genres in parallel so all rows render together
                val genreJobs = allSections.map { (genreSlug, _) ->
                    async(Dispatchers.IO) {
                        val genreCacheKey = "peliculas:$genreSlug:1"
                        if (CatalogCacheManager.get(genreCacheKey) == null) {
                            try {
                                val res = ApiClient.apiService.getCatalog(tipo = "peliculas", genre = genreSlug, page = 1)
                                if (res.success && res.data.isNotEmpty()) {
                                    CatalogCacheManager.put(genreCacheKey, res.data)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                mainJob.await()
                genreJobs.awaitAll()
            }
        } catch (_: Exception) {
        } finally {
            isLoadingMovies = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 70.dp, bottom = 60.dp)
        ) {
            // -------------------------------------------------------------
            // SECTION 1: HERO BANNER ROTATIVO DE 5 PELÍCULAS DESTACADAS (Reusable TvHeroBanner)
            // -------------------------------------------------------------
            item(key = "hero_banner", contentType = "hero_banner") {
                if (cleanMoviesList.isNotEmpty()) {
                    TvHeroBanner(
                        heroList = cleanMoviesList,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        firstKeyFocusRequester = firstKeyFocusRequester,
                        navFocusRequester = navFocusRequester,
                        scrollToRow = scrollToRow,
                        categoryBadgeText = "P E L Í C U L A   O R I G I N A L"
                    )
                } else if (isLoadingMovies) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .padding(start = 36.dp, end = 36.dp, bottom = 16.dp)
                    ) {
                        ShimmerBox(width = 1200.dp, height = 380.dp)
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 2: ESTRENOS Y SECCIONES ORGANIZADAS POR CADA GÉNERO
            // -------------------------------------------------------------
            if (cleanMoviesList.isNotEmpty()) {
                item(key = "novedades_row", contentType = "movie_row") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Novedades y Estrenos de Cine",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 36.dp)
                        ) {
                            items(cleanMoviesList.take(15), key = { "estreno_${it.id}" }, contentType = { "anime_card" }) { movie ->
                                val cardFocusRequester = remember(movie.id) { FocusRequester() }
                                TvAnimeCard(
                                    anime = movie,
                                    onClick = { onAnimeSelect(movie) },
                                    modifier = Modifier
                                        .width(165.dp)
                                        .focusRequester(cardFocusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                onFocusItem(cardFocusRequester)
                                                scrollToRow(1)
                                            }
                                        }
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                try { firstKeyFocusRequester.requestFocus() } catch (_: Exception) {}
                                                true
                                            } else false
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Lazy Loaded Rows for Popular Movie Genres
            itemsIndexed(allSections, key = { _, pair -> "sec_${pair.first}" }, contentType = { _, _ -> "genre_section" }) { idx, (genreSlug, sectionTitle) ->
                GenreMovieRowSection(
                    sectionTitle = sectionTitle,
                    genreSlug = genreSlug,
                    onAnimeSelect = onAnimeSelect,
                    onFocusItem = onFocusItem,
                    onFocused = { scrollToRow(2 + idx) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenreMovieRowSection(
    sectionTitle: String,
    genreSlug: String,
    onAnimeSelect: (AnimeItem) -> Unit,
    onFocusItem: (FocusRequester) -> Unit,
    onFocused: (() -> Unit)? = null
) {
    val movies = remember { mutableStateListOf<AnimeItem>() }
    var isLoading by remember { mutableStateOf(false) }
    var hasFetched by remember { mutableStateOf(false) }

    // Lazy load with In-Memory Cache Optimization
    LaunchedEffect(genreSlug) {
        if (!hasFetched) {
            hasFetched = true

            val cacheKey = "peliculas:$genreSlug:1"
            val cachedMovies = CatalogCacheManager.get(cacheKey)
            if (cachedMovies != null && cachedMovies.isNotEmpty()) {
                movies.clear()
                movies.addAll(cachedMovies)
                isLoading = false
            } else {
                isLoading = true
            }

            try {
                val res = ApiClient.apiService.getCatalog(tipo = "peliculas", genre = genreSlug, page = 1)
                if (res.success && res.data.isNotEmpty()) {
                    movies.clear()
                    movies.addAll(res.data)
                    CatalogCacheManager.put(cacheKey, res.data)
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    if (!isLoading && movies.isEmpty() && hasFetched) {
        return
    }

    if (isLoading || movies.isNotEmpty() || !hasFetched) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = sectionTitle,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
            )

            if (isLoading && movies.isEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(5) { ShimmerBox(width = 165.dp, height = 230.dp) }
                }
            } else {
                TvLazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 36.dp)
                ) {
                    items(movies, key = { "m_${genreSlug}_${it.id}" }, contentType = { "anime_card" }) { movie ->
                        val cardFocusRequester = remember(movie.id) { FocusRequester() }
                        TvAnimeCard(
                            anime = movie,
                            onClick = { onAnimeSelect(movie) },
                            modifier = Modifier
                                .width(165.dp)
                                .focusRequester(cardFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusItem(cardFocusRequester)
                                        onFocused?.invoke()
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
