package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.NetflixLogo
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvHeroBanner
import com.animetv.app.ui.components.TvTop10Card
import com.animetv.app.data.api.ApiClient
import com.animetv.app.ui.theme.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import androidx.core.graphics.ColorUtils
import coil.ImageLoader
import coil.request.ImageRequest
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
    isLoading: Boolean,
    onAnimeSelect: (AnimeItem) -> Unit,
    onEpisodeSelect: ((com.animetv.app.data.model.RecentEpisodeItem) -> Unit)? = null,
    onWatchHistorySelect: ((com.animetv.app.data.model.WatchHistoryItem) -> Unit)? = null,
    playButtonFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var ambientColor by remember { mutableStateOf<Color?>(null) }
    var selectedGenre by remember { mutableStateOf("Todos") }
    var currentHeroIndex by remember { mutableIntStateOf(0) }
    var isHeroFocused by remember { mutableStateOf(false) }

    val animatedAmbientColor by animateColorAsState(
        targetValue = ambientColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 800),
        label = "ambientColorAnimation"
    )

    val cleanHeroList = remember(heroList) {
        heroList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) && !it.slug.contains("user", ignoreCase = true) }
    }
    val cleanCatalogList = remember(catalogList) {
        catalogList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) && !it.slug.contains("user", ignoreCase = true) }
    }
    val cleanReleasingList = remember(releasingList) {
        releasingList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) && !it.slug.contains("user", ignoreCase = true) }
    }

    val watchHistory = remember { mutableStateListOf<com.animetv.app.data.model.WatchHistoryItem>() }

    DisposableEffect(context) {
        watchHistory.clear()
        watchHistory.addAll(com.animetv.app.data.repository.WatchHistoryManager.getHistory(context))
        onDispose {}
    }

    val rowWatchHistoryIndex = 2
    val rowLatestEpisodesIndex = if (watchHistory.isNotEmpty()) 3 else 2
    val offsetLatest = if (latestEpisodesList.isNotEmpty()) 1 else 0
    val rowReleasingIndex = (if (watchHistory.isNotEmpty()) 3 else 2) + offsetLatest
    val rowPopularIndex = rowReleasingIndex + 1
    val rowActionIndex = rowPopularIndex + 1

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

    // Restore focus with a slight delay when returning to HomeScreen
    LaunchedEffect(isScreenActive, lastSelectedAnime) {
        if (isScreenActive) {
            lastSelectedAnime?.let { anime ->
                val firstHero = cleanHeroList.firstOrNull() ?: heroList.firstOrNull()
                if (firstHero != null && anime.id == firstHero.id) {
                    delay(300)
                    try {
                        playButtonFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        e.printStackTrace()
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val scrollAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                (1f - (listState.firstVisibleItemScrollOffset / 250f)).coerceIn(0f, 1f)
            } else {
                0f
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 68.dp, bottom = 64.dp)
            ) {

                // -------------------------------------------------------------
                // FEATURE 1: HERO BANNER ROTATIVO / MULTI-HERO (Reusable TvHeroBanner)
                // -------------------------------------------------------------
                item(key = "hero_banner", contentType = "hero_banner") {
                    TvHeroBanner(
                        heroList = heroList,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        firstKeyFocusRequester = playButtonFocusRequester,
                        navFocusRequester = navFocusRequester,
                        scrollToRow = scrollToRow,
                        categoryBadgeText = "A N I M E   O R I G I N A L"
                    )
                }

                // -------------------------------------------------------------
                // FEATURE 2: FILA TOP 10 HOY (Números Gigantes)
                // -------------------------------------------------------------
                item {
                    val top10Items = remember(cleanReleasingList, cleanCatalogList) {
                        (cleanReleasingList.ifEmpty { cleanCatalogList }).take(10)
                    }
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
                            TvLazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                            ) {
                                itemsIndexed(top10Items) { rankIndex, anime ->
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
                                                    scrollToRow(1)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // ROW: CONTINUAR MIRANDO (Watch History)
                if (watchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Continuar mirando",
                                color = NetflixLightGrey,
                                fontSize = 15.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                            )
                            TvLazyRow(
                                modifier = Modifier.fillMaxWidth().height(310.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                            ) {
                                items(watchHistory) { historyItem ->
                                    val cardFocusRequester = remember(historyItem.animeId) { FocusRequester() }

                                    LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                        if (isScreenActive && lastSelectedAnime?.id == historyItem.animeId) {
                                            delay(300)
                                            try {
                                                cardFocusRequester.requestFocus()
                                            } catch (e: Exception) {
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
                                                    scrollToRow(rowWatchHistoryIndex)
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
                                color = NetflixLightGrey,
                                fontSize = 15.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                            )
                            TvLazyRow(
                                modifier = Modifier.fillMaxWidth().height(310.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                            ) {
                                items(latestEpisodesList) { episodeItem ->
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
                                                    scrollToRow(rowLatestEpisodesIndex)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // ROW: Nuevos lanzamientos (En emisión)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "En emisión",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                        )
                        val releasingRowItems = if (releasingList.isNotEmpty()) releasingList else catalogList
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(310.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                        ) {
                            items(releasingRowItems) { anime ->
                                val cardFocusRequester = remember(anime.id) { FocusRequester() }

                                LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                    if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                                        delay(300)
                                        try {
                                            cardFocusRequester.requestFocus()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
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
                                                scrollToRow(rowReleasingIndex)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // ROW: Popular en Anime TV
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Populares",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(310.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                        ) {
                            items(heroList.reversed()) { anime ->
                                val cardFocusRequester = remember(anime.id) { FocusRequester() }

                                LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                    if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                                        delay(300)
                                        try {
                                            cardFocusRequester.requestFocus()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
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
                                                scrollToRow(rowPopularIndex)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // ROW: Anime de acción
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Acción",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(310.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
                        ) {
                            items(catalogList.reversed()) { anime ->
                                val cardFocusRequester = remember(anime.id) { FocusRequester() }

                                LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                    if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                                        delay(300)
                                        try {
                                            cardFocusRequester.requestFocus()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
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
                                                scrollToRow(rowActionIndex)
                                            }
                                        }
                                )
                            }
                        }
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
