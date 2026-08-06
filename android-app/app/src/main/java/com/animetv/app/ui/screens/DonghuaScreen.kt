package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.repository.CatalogCacheManager
import com.animetv.app.data.repository.HomeCacheManager
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvHeroBanner
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
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
fun DonghuaScreen(
    isScreenActive: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val context = LocalContext.current
    var donghuaPage by remember { mutableIntStateOf(1) }
    val cachedDonghuaHero = remember { HomeCacheManager.getDonghuaHeroList(context) }
    val donghuaList = remember { mutableStateListOf<AnimeItem>().apply { addAll(cachedDonghuaHero) } }
    var isLoadingDonghua by remember { mutableStateOf(cachedDonghuaHero.isEmpty()) }
    var hasMorePages by remember { mutableStateOf(true) }

    val cleanDonghuaList = remember(donghuaList.size) {
        donghuaList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) }
    }

    // Focus restoration when returning to DonghuaScreen
    LaunchedEffect(isScreenActive, lastSelectedAnime) {
        if (isScreenActive) {
            lastSelectedAnime?.let { anime ->
                if (cleanDonghuaList.isNotEmpty() && anime.id == cleanDonghuaList.first().id) {
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
            "accion" to "Acción y Artes Marciales (Cultivo)",
            "aventura" to "Aventura y Fantasía Chino",
            "comedia" to "Comedia en Donghua",
            "romance" to "Romance y Fantasía Histórica",
            "sci-fi" to "Ciencia Ficción y Mecha",
            "sobrenatural" to "Sobrenatural y Magia",
            "drama" to "Drama y Misterio"
        )
    }

    // Load Donghua & Genre Sections in Parallel (Synchronized Instant Loading)
    LaunchedEffect(Unit) {
        donghuaPage = 1
        hasMorePages = true

        val cacheKey = "donghua:all:1"
        val cachedDonghua = CatalogCacheManager.get(context, cacheKey)

        if (cachedDonghua != null && cachedDonghua.isNotEmpty()) {
            donghuaList.clear()
            donghuaList.addAll(cachedDonghua)
            isLoadingDonghua = false
        } else {
            isLoadingDonghua = true
        }

        try {
            coroutineScope {
                // 1. Fetch main catalog & pre-enrich top 5 hero anime with TMDB backdrops
                val mainJob = async(Dispatchers.IO) {
                    try {
                        val res = ApiClient.apiService.getCatalog(categoria = "donghua", page = 1)
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
                                donghuaList.clear()
                                donghuaList.addAll(fullList)
                            }
                            CatalogCacheManager.put(context, cacheKey, fullList)
                            HomeCacheManager.saveDonghuaHeroData(context, fullList.take(5))
                        }
                    } catch (_: Exception) {}
                }

                // 2. Fetch popular donghua genres in smooth batches of 3
                val genreJob = async(Dispatchers.IO) {
                    for (batch in allSections.chunked(3)) {
                        batch.map { (genreSlug, _) ->
                            async(Dispatchers.IO) {
                                val genreCacheKey = "donghua:$genreSlug:1"
                                if (CatalogCacheManager.get(context, genreCacheKey) == null) {
                                    try {
                                        val res = ApiClient.apiService.getCatalog(categoria = "donghua", genre = genreSlug, page = 1)
                                        if (res.success && res.data.isNotEmpty()) {
                                            CatalogCacheManager.put(context, genreCacheKey, res.data)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }.awaitAll()
                    }
                }

                mainJob.await()
                genreJob.await()
            }
        } catch (_: Exception) {
        } finally {
            isLoadingDonghua = false
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
            // SECTION 1: HERO BANNER ROTATIVO DE 5 DONGHUA DESTACADOS
            // -------------------------------------------------------------
            item(key = "hero_banner", contentType = "hero_banner") {
                if (cleanDonghuaList.isNotEmpty()) {
                    TvHeroBanner(
                        heroList = cleanDonghuaList,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        firstKeyFocusRequester = firstKeyFocusRequester,
                        navFocusRequester = navFocusRequester,
                        scrollToRow = scrollToRow,
                        categoryBadgeText = "D O N G H U A   A N I M E"
                    )
                } else if (isLoadingDonghua) {
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
            // SECTION 2: NOVEDADES Y ESTRENOS DE DONGHUA
            // -------------------------------------------------------------
            if (cleanDonghuaList.isNotEmpty()) {
                item(key = "novedades_donghua_row", contentType = "donghua_row") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Novedades y Estrenos de Donghua",
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
                            items(cleanDonghuaList.take(15), key = { "estreno_donghua_${it.id}" }, contentType = { "anime_card" }) { anime ->
                                val cardFocusRequester = remember(anime.id) { FocusRequester() }
                                TvAnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeSelect(anime) },
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

            // Lazy Loaded Rows for Popular Donghua Genres
            itemsIndexed(allSections, key = { _, pair -> "sec_donghua_${pair.first}" }, contentType = { _, _ -> "genre_section" }) { idx, (genreSlug, sectionTitle) ->
                GenreDonghuaRowSection(
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
fun GenreDonghuaRowSection(
    sectionTitle: String,
    genreSlug: String,
    onAnimeSelect: (AnimeItem) -> Unit,
    onFocusItem: (FocusRequester) -> Unit,
    onFocused: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val animeList = remember { mutableStateListOf<AnimeItem>() }
    var isLoading by remember { mutableStateOf(false) }
    var hasFetched by remember { mutableStateOf(false) }

    // Lazy load with In-Memory + Disk Cache Optimization
    LaunchedEffect(genreSlug) {
        if (!hasFetched) {
            hasFetched = true

            val cacheKey = "donghua:$genreSlug:1"
            val cachedAnime = CatalogCacheManager.get(context, cacheKey)
            if (cachedAnime != null && cachedAnime.isNotEmpty()) {
                animeList.clear()
                animeList.addAll(cachedAnime)
                isLoading = false
            } else {
                isLoading = true
                try {
                    val res = ApiClient.apiService.getCatalog(categoria = "donghua", genre = genreSlug, page = 1)
                    if (res.success && res.data.isNotEmpty()) {
                        animeList.clear()
                        animeList.addAll(res.data)
                        CatalogCacheManager.put(context, cacheKey, res.data)
                    }
                } catch (_: Exception) {
                } finally {
                    isLoading = false
                }
            }
        }
    }

    if (!isLoading && animeList.isEmpty() && hasFetched) {
        return
    }

    if (isLoading || animeList.isNotEmpty() || !hasFetched) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = sectionTitle,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
            )

            if (isLoading && animeList.isEmpty()) {
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
                    items(animeList, key = { "d_${genreSlug}_${it.id}" }, contentType = { "anime_card" }) { anime ->
                        val cardFocusRequester = remember(anime.id) { FocusRequester() }
                        TvAnimeCard(
                            anime = anime,
                            onClick = { onAnimeSelect(anime) },
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
