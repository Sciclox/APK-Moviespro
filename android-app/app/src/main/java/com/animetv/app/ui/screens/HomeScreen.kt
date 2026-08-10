package com.animetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.PaginatedGenreRow
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvHeroBanner
import com.animetv.app.ui.components.TvTop10Card
import com.animetv.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    isScreenActive: Boolean = false,
    isNavFocused: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    heroList: List<AnimeItem>,
    catalogList: List<AnimeItem>,
    releasingList: List<AnimeItem>,
    latestEpisodesList: List<com.animetv.app.data.model.RecentEpisodeItem> = emptyList(),
    shounenList: List<AnimeItem> = emptyList(),
    romanceList: List<AnimeItem> = emptyList(),
    comedyList: List<AnimeItem> = emptyList(),
    fantasyList: List<AnimeItem> = emptyList(),
    supernaturalList: List<AnimeItem> = emptyList(),
    moviesList: List<AnimeItem> = emptyList(),
    isLoading: Boolean,
    onAnimeSelect: (AnimeItem) -> Unit,
    onEpisodeSelect: ((com.animetv.app.data.model.RecentEpisodeItem) -> Unit)? = null,
    onWatchHistorySelect: ((com.animetv.app.data.model.WatchHistoryItem) -> Unit)? = null,
    playButtonFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetFocusedRowIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(targetFocusedRowIndex) {
        if (targetFocusedRowIndex >= 0) {
            delay(100)
            try {
                val totalItems = listState.layoutInfo.totalItemsCount
                if (targetFocusedRowIndex in 0 until totalItems) {
                    listState.animateScrollToItem(targetFocusedRowIndex, 0)
                }
            } catch (_: Exception) {}
        }
    }

    val cleanHeroList = remember(heroList) {
        heroList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) && !it.slug.contains("user", ignoreCase = true) }
    }

    val watchHistory = remember { mutableStateListOf<com.animetv.app.data.model.WatchHistoryItem>() }

    LaunchedEffect(context) {
        watchHistory.clear()
        watchHistory.addAll(com.animetv.app.data.repository.WatchHistoryManager.getHistory(context))
    }

    // Restore focus with a slight delay when returning to HomeScreen
    LaunchedEffect(isScreenActive, lastSelectedAnime) {
        if (isScreenActive) {
            lastSelectedAnime?.let { anime ->
                val firstHero = cleanHeroList.firstOrNull() ?: heroList.firstOrNull()
                if (firstHero != null && anime.id == firstHero.id) {
                    delay(300)
                    try {
                        playButtonFocusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // Land initial focus on the "Inicio" (Home) icon in TopNav upon app launch
    LaunchedEffect(Unit) {
        if (lastSelectedAnime == null) {
            try {
                navFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        if (isLoading) {
            SkeletonHomeScreen()
        } else {
            var rowIndexCounter = 0
            val heroRowIndex = rowIndexCounter++
            val top10RowIndex = rowIndexCounter++
            val historyRowIndex = if (watchHistory.isNotEmpty()) rowIndexCounter++ else -1
            val episodesRowIndex = if (latestEpisodesList.isNotEmpty()) rowIndexCounter++ else -1
            val releasingRowIndex = rowIndexCounter++
            val popularRowIndex = rowIndexCounter++
            val actionRowIndex = rowIndexCounter++
            val shounenRowIndex = rowIndexCounter++
            val romanceRowIndex = rowIndexCounter++
            val comedyRowIndex = rowIndexCounter++
            val fantasyRowIndex = rowIndexCounter++
            val supernaturalRowIndex = rowIndexCounter++
            val moviesRowIndex = rowIndexCounter++

            val cleanCatalog = remember(catalogList) { catalogList.filter { !it.slug.contains("perfil", ignoreCase = true) } }

            val displayShounen = remember(shounenList, cleanCatalog) {
                shounenList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty {
                    cleanCatalog.filter { it.title.contains("shounen", true) || it.title.contains("dragon", true) || it.title.contains("piece", true) || it.title.contains("hero", true) || (it.synopsis?.contains("batalla", true) == true) }.ifEmpty { cleanCatalog.drop(1) }
                }
            }

            val displayRomance = remember(romanceList, cleanCatalog) {
                romanceList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty {
                    cleanCatalog.filter { it.title.contains("love", true) || it.title.contains("romance", true) || it.title.contains("amor", true) || (it.synopsis?.contains("amor", true) == true) }.ifEmpty { cleanCatalog.drop(3) }
                }
            }

            val displayComedy = remember(comedyList, cleanCatalog) {
                comedyList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty {
                    cleanCatalog.filter { it.title.contains("comedia", true) || it.title.contains("comedy", true) || (it.synopsis?.contains("comedia", true) == true) }.ifEmpty { cleanCatalog.drop(5) }
                }
            }

            val displayFantasy = remember(fantasyList, cleanCatalog, releasingList) {
                fantasyList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty {
                    cleanCatalog.filter { it.title.contains("isekai", true) || it.title.contains("fantasy", true) || it.title.contains("maou", true) || (it.synopsis?.contains("fantasía", true) == true) }.ifEmpty { releasingList.reversed() }
                }
            }

            val displaySupernatural = remember(supernaturalList, cleanCatalog, heroList) {
                supernaturalList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty {
                    cleanCatalog.filter { it.title.contains("demon", true) || it.title.contains("ghost", true) || it.title.contains("misterio", true) || (it.synopsis?.contains("sobrenatural", true) == true) }.ifEmpty { heroList.filter { !it.slug.contains("perfil", ignoreCase = true) } }
                }
            }

            val displayMovies = remember(moviesList, cleanCatalog) {
                moviesList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty {
                    cleanCatalog.filter { (it.type?.contains("Movie", true) == true || it.type?.contains("Película", true) == true || it.slug.contains("movie", true) || it.title.contains("movie", true) || it.title.contains("pelicula", true)) }.ifEmpty { cleanCatalog.takeLast(10) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 68.dp, bottom = 64.dp)
            ) {
                // FEATURE 1: HERO BANNER
                item(key = "hero_banner") {
                    TvHeroBanner(
                        heroList = heroList,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        firstKeyFocusRequester = playButtonFocusRequester,
                        navFocusRequester = navFocusRequester,
                        scrollToRow = { targetFocusedRowIndex = heroRowIndex },
                        categoryBadgeText = "A N I M E   O R I G I N A L"
                    )
                }

                // FEATURE 2: FILA TOP 10 HOY
                item {
                    val cleanReleasingList = releasingList.filter { !it.slug.contains("perfil", ignoreCase = true) }
                    val cleanCatalogList = catalogList.filter { !it.slug.contains("perfil", ignoreCase = true) }
                    val top10Items = (cleanReleasingList.ifEmpty { cleanCatalogList }).take(10)

                    if (top10Items.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Top 10 hoy",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                            ) {
                                itemsIndexed(items = top10Items, key = { _, anime -> anime.id }) { rankIndex, anime ->
                                    val cardFocusRequester = remember(anime.id) { FocusRequester() }
                                    TvTop10Card(
                                        anime = anime,
                                        rank = rankIndex + 1,
                                        onClick = { onAnimeSelect(anime) },
                                        modifier = Modifier
                                            .focusRequester(cardFocusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    onFocusItem(cardFocusRequester)
                                                    targetFocusedRowIndex = top10RowIndex
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // ROW: CONTINUAR VIENDO
                if (watchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Continuar viendo",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(310.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                            ) {
                                items(items = watchHistory, key = { it.animeId }) { historyItem ->
                                    val cardFocusRequester = remember(historyItem.animeId) { FocusRequester() }

                                    LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                        if (isScreenActive && lastSelectedAnime?.id == historyItem.animeId) {
                                            delay(300)
                                            try {
                                                cardFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }

                                    com.animetv.app.ui.components.TvHistoryCard(
                                        historyItem = historyItem,
                                        onClick = {
                                            if (onWatchHistorySelect != null) {
                                                onWatchHistorySelect(historyItem)
                                            } else {
                                                onAnimeSelect(
                                                    AnimeItem(
                                                        id = historyItem.animeId,
                                                        title = historyItem.animeTitle,
                                                        slug = historyItem.animeSlug,
                                                        image = historyItem.animeImage,
                                                        backdrop = historyItem.backdrop,
                                                        type = "TV",
                                                        synopsis = null
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .focusRequester(cardFocusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    onFocusItem(cardFocusRequester)
                                                    targetFocusedRowIndex = historyRowIndex
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // ROW: Últimos Episodios Subidos
                if (latestEpisodesList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Últimos Episodios Subidos",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(310.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                            ) {
                                items(items = latestEpisodesList, key = { it.id }) { episodeItem ->
                                    val cardFocusRequester = remember(episodeItem.id) { FocusRequester() }

                                    com.animetv.app.ui.components.TvEpisodeCard(
                                        episodeItem = episodeItem,
                                        onClick = {
                                            if (onEpisodeSelect != null) {
                                                onEpisodeSelect(episodeItem)
                                            } else {
                                                onAnimeSelect(
                                                    AnimeItem(
                                                        id = episodeItem.slug,
                                                        title = episodeItem.title,
                                                        slug = episodeItem.slug,
                                                        image = episodeItem.image,
                                                        backdrop = episodeItem.backdrop,
                                                        type = "TV",
                                                        synopsis = episodeItem.synopsis
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .width(190.dp)
                                            .focusRequester(cardFocusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    onFocusItem(cardFocusRequester)
                                                    targetFocusedRowIndex = episodesRowIndex
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // ROW: Nuevos lanzamientos
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "En emisión",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                        )
                        val releasingRowItems = releasingList.filter { !it.slug.contains("perfil", ignoreCase = true) }.ifEmpty { catalogList.filter { !it.slug.contains("perfil", ignoreCase = true) } }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(310.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                        ) {
                            items(items = releasingRowItems, key = { it.id }) { anime ->
                                val cardFocusRequester = remember(anime.id) { FocusRequester() }

                                LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                    if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                                        delay(300)
                                        try {
                                            cardFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }

                                TvAnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeSelect(anime) },
                                    modifier = Modifier
                                        .width(190.dp)
                                        .focusRequester(cardFocusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                onFocusItem(cardFocusRequester)
                                                targetFocusedRowIndex = releasingRowIndex
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // ROW: Populares
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Populares",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(310.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                        ) {
                            items(items = heroList.reversed(), key = { it.id }) { anime ->
                                val cardFocusRequester = remember(anime.id) { FocusRequester() }

                                LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                    if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                                        delay(300)
                                        try {
                                            cardFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }

                                TvAnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeSelect(anime) },
                                    modifier = Modifier
                                        .width(190.dp)
                                        .focusRequester(cardFocusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                onFocusItem(cardFocusRequester)
                                                targetFocusedRowIndex = popularRowIndex
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // ROW: Acción
                item {
                    PaginatedGenreRow(
                        title = "Acción",
                        genreKey = "Accion",
                        initialItems = catalogList.reversed(),
                        isScreenActive = isScreenActive,
                        lastSelectedAnime = lastSelectedAnime,
                        rowIndex = actionRowIndex,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        onFocusedRowChanged = { targetFocusedRowIndex = it }
                    )
                }

                // ROW: Shounen & Aventura
                if (displayShounen.isNotEmpty()) {
                    item {
                        PaginatedGenreRow(
                            title = "Shounen & Aventura",
                            genreKey = "shounen",
                            initialItems = displayShounen,
                            isScreenActive = isScreenActive,
                            lastSelectedAnime = lastSelectedAnime,
                            rowIndex = shounenRowIndex,
                            onAnimeSelect = onAnimeSelect,
                            onFocusItem = onFocusItem,
                            onFocusedRowChanged = { targetFocusedRowIndex = it }
                        )
                    }
                }

                // ROW: Romance & Drama
                if (displayRomance.isNotEmpty()) {
                    item {
                        PaginatedGenreRow(
                            title = "Romance & Drama",
                            genreKey = "romance",
                            initialItems = displayRomance,
                            isScreenActive = isScreenActive,
                            lastSelectedAnime = lastSelectedAnime,
                            rowIndex = romanceRowIndex,
                            onAnimeSelect = onAnimeSelect,
                            onFocusItem = onFocusItem,
                            onFocusedRowChanged = { targetFocusedRowIndex = it }
                        )
                    }
                }

                // ROW: Comedia & Slice of Life
                if (displayComedy.isNotEmpty()) {
                    item {
                        PaginatedGenreRow(
                            title = "Comedia & Slice of Life",
                            genreKey = "comedia",
                            initialItems = displayComedy,
                            isScreenActive = isScreenActive,
                            lastSelectedAnime = lastSelectedAnime,
                            rowIndex = comedyRowIndex,
                            onAnimeSelect = onAnimeSelect,
                            onFocusItem = onFocusItem,
                            onFocusedRowChanged = { targetFocusedRowIndex = it }
                        )
                    }
                }

                // ROW: Fantasía e Isekai
                if (displayFantasy.isNotEmpty()) {
                    item {
                        PaginatedGenreRow(
                            title = "Fantasía e Isekai",
                            genreKey = "fantasia",
                            initialItems = displayFantasy,
                            isScreenActive = isScreenActive,
                            lastSelectedAnime = lastSelectedAnime,
                            rowIndex = fantasyRowIndex,
                            onAnimeSelect = onAnimeSelect,
                            onFocusItem = onFocusItem,
                            onFocusedRowChanged = { targetFocusedRowIndex = it }
                        )
                    }
                }

                // ROW: Sobrenatural & Terror
                if (displaySupernatural.isNotEmpty()) {
                    item {
                        PaginatedGenreRow(
                            title = "Sobrenatural & Terror",
                            genreKey = "sobrenatural",
                            initialItems = displaySupernatural,
                            isScreenActive = isScreenActive,
                            lastSelectedAnime = lastSelectedAnime,
                            rowIndex = supernaturalRowIndex,
                            onAnimeSelect = onAnimeSelect,
                            onFocusItem = onFocusItem,
                            onFocusedRowChanged = { targetFocusedRowIndex = it }
                        )
                    }
                }

                // ROW: Películas Anime & Especiales
                if (displayMovies.isNotEmpty()) {
                    item {
                        PaginatedGenreRow(
                            title = "Películas Anime & Especiales",
                            tipoKey = "pelicula",
                            initialItems = displayMovies,
                            isScreenActive = isScreenActive,
                            lastSelectedAnime = lastSelectedAnime,
                            rowIndex = moviesRowIndex,
                            onAnimeSelect = onAnimeSelect,
                            onFocusItem = onFocusItem,
                            onFocusedRowChanged = { targetFocusedRowIndex = it }
                        )
                    }
                }

                // BOTTOM SPACER
                item {
                    Spacer(modifier = Modifier.height(350.dp))
                }
            }
        }
    }
}



@Composable
fun SkeletonHomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 36.dp, top = 36.dp)
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .width(420.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}
