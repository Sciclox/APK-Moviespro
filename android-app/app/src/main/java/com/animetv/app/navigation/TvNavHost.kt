package com.animetv.app.navigation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.*
import com.animetv.app.data.repository.AnimeRepository
import com.animetv.app.data.repository.HomeCacheManager
import com.animetv.app.data.repository.WatchHistoryManager
import com.animetv.app.ui.components.NetflixTopNav
import com.animetv.app.ui.screens.*
import com.animetv.app.ui.theme.AnimeTvTheme
import com.animetv.app.ui.theme.NetflixBlack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimeTvApp(activityIntent: Intent?) {
    AnimeTvTheme {
        val context = LocalContext.current
        val screenStack = remember { mutableStateListOf(TvScreen.SPLASH) }
        val currentScreen = screenStack.lastOrNull() ?: TvScreen.HOME
        var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
        var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }

        // Deep Link Handler for Android TV 'Seguir viendo' (Watch Next) System Channel
        LaunchedEffect(activityIntent) {
            val intentUri = activityIntent?.data
            if (intentUri != null && intentUri.scheme == "animetv" && intentUri.host == "play") {
                val animeSlug = intentUri.getQueryParameter("slug") ?: ""
                val epNumStr = intentUri.getQueryParameter("ep") ?: "1"
                val epNum = epNumStr.toIntOrNull() ?: 1

                if (animeSlug.isNotBlank()) {
                    val historyMatch = WatchHistoryManager.getHistory(context)
                        .find { it.animeSlug == animeSlug || it.animeId == animeSlug }

                    selectedAnime = AnimeItem(
                        id = historyMatch?.animeId ?: animeSlug,
                        title = historyMatch?.animeTitle ?: animeSlug.replace("-", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                        slug = animeSlug,
                        image = historyMatch?.animeImage ?: "",
                        backdrop = historyMatch?.backdrop
                    )

                    selectedEpisode = EpisodeItem(
                        id = "$animeSlug-episode-$epNum",
                        number = epNum,
                        title = historyMatch?.episodeTitle?.ifEmpty { "Episodio $epNum" } ?: "Episodio $epNum",
                        slug = "$animeSlug/$epNum"
                    )

                    if (screenStack.lastOrNull() != TvScreen.PLAYER) {
                        screenStack.add(TvScreen.PLAYER)
                    }
                }
            }
        }

        // TopNav Focus Requesters
        val searchNavFocusRequester = remember { FocusRequester() }
        val homeNavFocusRequester = remember { FocusRequester() }
        val myListNavFocusRequester = remember { FocusRequester() }

        val seriesNavFocusRequester = remember { FocusRequester() }
        val peliculasNavFocusRequester = remember { FocusRequester() }
        val kdramasNavFocusRequester = remember { FocusRequester() }

        val navFocusRequesters = remember {
            mapOf(
                "series" to seriesNavFocusRequester,
                "peliculas" to peliculasNavFocusRequester,
                "kdramas" to kdramasNavFocusRequester
            )
        }

        var isNavFocused by remember { mutableStateOf(false) }

        // Content Focus Requesters
        val homePlayButtonFocusRequester = remember { FocusRequester() }
        val searchFirstKeyFocusRequester = remember { FocusRequester() }
        val myListFirstCardFocusRequester = remember { FocusRequester() }

        val seriesFirstKeyFocusRequester = remember { FocusRequester() }
        val peliculasFirstKeyFocusRequester = remember { FocusRequester() }
        val kdramasFirstKeyFocusRequester = remember { FocusRequester() }

        var lastFocusedRequester by remember { mutableStateOf<FocusRequester?>(null) }

        // Cache state
        val cachedHero = remember { HomeCacheManager.getHeroList(context) }
        val cachedCatalog = remember { HomeCacheManager.getCatalogList(context) }
        val cachedReleasing = remember { HomeCacheManager.getReleasingList(context) }

        var homeHeroList by remember { mutableStateOf<List<AnimeItem>>(cachedHero) }
        var homeCatalogList by remember { mutableStateOf<List<AnimeItem>>(cachedCatalog) }
        var homeReleasingList by remember { mutableStateOf<List<AnimeItem>>(cachedReleasing) }
        var homeLatestEpisodesList by remember { mutableStateOf<List<RecentEpisodeItem>>(emptyList()) }
        var homeShounenList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
        var homeRomanceList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
        var homeComedyList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
        var homeFantasyList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
        var homeSupernaturalList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
        var homeMoviesList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
        var homeIsLoading by remember { mutableStateOf(cachedHero.isEmpty() && cachedCatalog.isEmpty()) }

        // Search State
        var persistentSearchQuery by remember { mutableStateOf("") }
        var persistentSearchResults by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }

        val defaultFallbackList = remember {
            listOf(
                AnimeItem(id = "sword-art-online", title = "Sword Art Online", slug = "sword-art-online", image = "https://cdn.animeav1.com/covers/222.jpg", backdrop = "https://image.tmdb.org/t/p/w780/6A8IHWrtMLtadNXnMTjggvVY8cs.jpg", type = "TV", synopsis = "En el año 2022, el videojuego de realidad virtual Sword Art Online es lanzado."),
                AnimeItem(id = "frieren", title = "Frieren: Beyond Journey's End", slug = "frieren", image = "https://image.tmdb.org/t/p/w780/6A8IHWrtMLtadNXnMTjggvVY8cs.jpg", backdrop = "https://image.tmdb.org/t/p/w780/vIkH7fUQf8Olo8Apq56FQLGDXOo.jpg", type = "TV", synopsis = "La maga elfa Frieren emprende un viaje tras la muerte de su compañero héroe."),
                AnimeItem(id = "attack-on-titan", title = "Attack on Titan", slug = "attack-on-titan", image = "https://image.tmdb.org/t/p/w780/yFPQ4JhhirnCVe2UIKGMYX7TOGZ.jpg", backdrop = "https://image.tmdb.org/t/p/w780/yFPQ4JhhirnCVe2UIKGMYX7TOGZ.jpg", type = "TV", synopsis = "La humanidad vive atrapada tras enormes murallas para protegerse de los Titanes."),
                AnimeItem(id = "fullmetal-alchemist", title = "Fullmetal Alchemist", slug = "fullmetal-alchemist", image = "https://image.tmdb.org/t/p/w780/vIkH7fUQf8Olo8Apq56FQLGDXOo.jpg", backdrop = "https://image.tmdb.org/t/p/w780/vIkH7fUQf8Olo8Apq56FQLGDXOo.jpg", type = "TV", synopsis = "Dos hermanos buscan la piedra filosofal para recuperar sus cuerpos.")
            )
        }

        // Parallel API load for ZonaAPI
        LaunchedEffect(Unit) {
            try {
                coroutineScope {
                    val p1Deferred = async { ApiClient.apiService.getList(page = 1) }
                    val p2Deferred = async { ApiClient.apiService.getList(page = 2) }
                    val p3Deferred = async { ApiClient.apiService.getList(page = 3) }
                    val p4Deferred = async { ApiClient.apiService.getList(page = 4) }

                    try {
                        val p1 = p1Deferred.await()
                        if (p1.featured.isNotEmpty()) {
                            homeHeroList = p1.featured.map { it.toAnimeItem() }
                        }
                        if (p1.movies.isNotEmpty()) {
                            homeCatalogList = p1.movies.map { it.toAnimeItem() }
                        }
                    } catch (_: Exception) {}

                    try {
                        val p2 = p2Deferred.await()
                        if (p2.movies.isNotEmpty()) {
                            homeReleasingList = p2.movies.map { it.toAnimeItem() }
                        }
                    } catch (_: Exception) {}

                    try {
                        val p3 = p3Deferred.await()
                        if (p3.movies.isNotEmpty()) {
                            homeShounenList = p3.movies.map { it.toAnimeItem() }
                        }
                    } catch (_: Exception) {}

                    try {
                        val p4 = p4Deferred.await()
                        if (p4.movies.isNotEmpty()) {
                            homeRomanceList = p4.movies.map { it.toAnimeItem() }
                        }
                    } catch (_: Exception) {}
                }

                HomeCacheManager.saveHomeData(context, homeHeroList, homeCatalogList, homeReleasingList)
                AnimeRepository.preloadCatalog(16)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (homeHeroList.isEmpty()) homeHeroList = defaultFallbackList
                if (homeCatalogList.isEmpty()) homeCatalogList = defaultFallbackList
                if (homeReleasingList.isEmpty()) homeReleasingList = homeCatalogList
                homeIsLoading = false
            }
        }

        fun popBackStack() {
            if (screenStack.size > 1) {
                screenStack.removeAt(screenStack.lastIndex)
            }
        }

        fun pushScreen(screen: TvScreen) {
            if (screenStack.lastOrNull() != screen) {
                screenStack.add(screen)
            }
        }

        val mainTabScreens = remember {
            setOf(
                TvScreen.HOME, TvScreen.SEARCH, TvScreen.SERIES, TvScreen.PELICULAS,
                TvScreen.KDRAMAS, TvScreen.MYLIST
            )
        }

        val showTopNav = currentScreen in mainTabScreens

        BackHandler(enabled = screenStack.size > 1 && currentScreen != TvScreen.SPLASH) {
            popBackStack()
        }

        BackHandler(enabled = showTopNav && !isNavFocused) {
            try {
                when (currentScreen) {
                    TvScreen.HOME -> homeNavFocusRequester.requestFocus()
                    TvScreen.SEARCH -> searchNavFocusRequester.requestFocus()
                    TvScreen.SERIES -> seriesNavFocusRequester.requestFocus()
                    TvScreen.PELICULAS -> peliculasNavFocusRequester.requestFocus()
                    TvScreen.KDRAMAS -> kdramasNavFocusRequester.requestFocus()
                    TvScreen.MYLIST -> myListNavFocusRequester.requestFocus()
                    else -> {}
                }
            } catch (_: Exception) {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixBlack)
        ) {
            val allMainTabs = remember {
                setOf(
                    TvScreen.HOME, TvScreen.SEARCH, TvScreen.SERIES,
                    TvScreen.PELICULAS, TvScreen.KDRAMAS, TvScreen.MYLIST, TvScreen.SPLASH
                )
            }
            val activeTabScreen = remember(currentScreen, screenStack.size) {
                screenStack.findLast { it in allMainTabs } ?: TvScreen.HOME
            }

            val isOverlayActive = currentScreen == TvScreen.DETAIL || currentScreen == TvScreen.PLAYER

            // Global Focus Restoration on returning from DETAIL or PLAYER screens
            LaunchedEffect(currentScreen) {
                if (currentScreen in allMainTabs) {
                    delay(300)
                    lastFocusedRequester?.let { requester ->
                        try {
                            requester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }
            }

            val mainTabScale by animateFloatAsState(
                targetValue = if (isOverlayActive) 1.04f else 1.0f,
                animationSpec = tween(750, easing = LinearOutSlowInEasing),
                label = "mainTabScale"
            )
            val mainTabAlpha by animateFloatAsState(
                targetValue = if (isOverlayActive) 0.0f else 1.0f,
                animationSpec = tween(450, easing = FastOutLinearInEasing),
                label = "mainTabAlpha"
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // Persistently mounted Tab Content Container (Preserves D-Pad Focus on return)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = mainTabScale
                            scaleY = mainTabScale
                            alpha = mainTabAlpha
                        }
                        .focusProperties {
                            canFocus = !isOverlayActive
                        }
                ) {
                    AnimatedContent(
                        targetState = activeTabScreen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (targetState == TvScreen.HOME && initialState == TvScreen.SPLASH) {
                                fadeIn(tween(500)) togetherWith fadeOut(tween(300))
                            } else {
                                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                            }
                        },
                        label = "tab_transition"
                    ) { tabScreen ->
                        val isVisible = tabScreen == activeTabScreen && !isOverlayActive
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties {
                                    canFocus = isVisible
                                }
                        ) {
                            when (tabScreen) {
                                TvScreen.SPLASH -> {
                                    SplashScreen(onFinished = {
                                        screenStack.clear()
                                        screenStack.add(TvScreen.HOME)
                                    })
                                }
                                TvScreen.HOME -> {
                                    HomeScreen(
                                        isScreenActive = (currentScreen == TvScreen.HOME),
                                        isNavFocused = isNavFocused,
                                        lastSelectedAnime = selectedAnime,
                                        heroList = homeHeroList,
                                        catalogList = homeCatalogList,
                                        releasingList = homeReleasingList,
                                        latestEpisodesList = homeLatestEpisodesList,
                                        shounenList = homeShounenList,
                                        romanceList = homeRomanceList,
                                        comedyList = homeComedyList,
                                        fantasyList = homeFantasyList,
                                        supernaturalList = homeSupernaturalList,
                                        moviesList = homeMoviesList,
                                        isLoading = homeIsLoading,
                                        onAnimeSelect = { anime ->
                                            selectedAnime = anime
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        onEpisodeSelect = { episodeItem ->
                                            selectedAnime = AnimeItem(
                                                id = episodeItem.slug,
                                                title = episodeItem.title,
                                                slug = episodeItem.slug,
                                                image = episodeItem.image,
                                                backdrop = episodeItem.backdrop,
                                                type = "TV",
                                                synopsis = episodeItem.synopsis
                                            )
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        onWatchHistorySelect = { historyItem ->
                                            selectedAnime = AnimeItem(
                                                id = historyItem.animeId,
                                                title = historyItem.animeTitle,
                                                slug = historyItem.animeSlug,
                                                image = historyItem.animeImage,
                                                backdrop = historyItem.backdrop,
                                                type = "TV",
                                                synopsis = null
                                            )
                                            val epTitle = historyItem.episodeTitle.ifEmpty { "Episodio ${historyItem.episodeNumber}" }
                                            val epSlug = if (historyItem.animeSlug.contains("/")) historyItem.animeSlug else "${historyItem.animeSlug}/${historyItem.episodeNumber}"
                                            selectedEpisode = EpisodeItem(
                                                id = "${historyItem.animeSlug}-episode-${historyItem.episodeNumber}",
                                                number = historyItem.episodeNumber,
                                                title = epTitle,
                                                slug = epSlug
                                            )
                                            pushScreen(TvScreen.PLAYER)
                                        },
                                        playButtonFocusRequester = homePlayButtonFocusRequester,
                                        navFocusRequester = homeNavFocusRequester,
                                        onFocusItem = { lastFocusedRequester = it }
                                    )
                                }
                                TvScreen.SEARCH -> {
                                    SearchScreen(
                                        isScreenActive = (currentScreen == TvScreen.SEARCH),
                                        isNavFocused = isNavFocused,
                                        lastSelectedAnime = selectedAnime,
                                        savedQuery = persistentSearchQuery,
                                        savedResults = persistentSearchResults,
                                        onSearchStateChange = { q, r ->
                                            persistentSearchQuery = q
                                            persistentSearchResults = r
                                        },
                                        onAnimeSelect = { anime ->
                                            selectedAnime = anime
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        firstKeyFocusRequester = searchFirstKeyFocusRequester,
                                        navFocusRequester = searchNavFocusRequester,
                                        onFocusItem = { lastFocusedRequester = it }
                                    )
                                }
                                TvScreen.SERIES -> {
                                    SeriesScreen(
                                        isScreenActive = (currentScreen == TvScreen.SERIES),
                                        lastSelectedAnime = selectedAnime,
                                        onAnimeSelect = { anime ->
                                            selectedAnime = anime
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        firstKeyFocusRequester = seriesFirstKeyFocusRequester,
                                        navFocusRequester = seriesNavFocusRequester,
                                        onFocusItem = { lastFocusedRequester = it }
                                    )
                                }
                                TvScreen.PELICULAS -> {
                                    MoviesScreen(
                                        isScreenActive = (currentScreen == TvScreen.PELICULAS),
                                        lastSelectedAnime = selectedAnime,
                                        onAnimeSelect = { anime ->
                                            selectedAnime = anime
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        firstKeyFocusRequester = peliculasFirstKeyFocusRequester,
                                        navFocusRequester = peliculasNavFocusRequester,
                                        onFocusItem = { lastFocusedRequester = it }
                                    )
                                }
                                TvScreen.KDRAMAS -> {
                                    KdramasScreen(
                                        isScreenActive = (currentScreen == TvScreen.KDRAMAS),
                                        lastSelectedAnime = selectedAnime,
                                        onAnimeSelect = { anime ->
                                            selectedAnime = anime
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        firstKeyFocusRequester = kdramasFirstKeyFocusRequester,
                                        navFocusRequester = kdramasNavFocusRequester,
                                        onFocusItem = { lastFocusedRequester = it }
                                    )
                                }

                                TvScreen.MYLIST -> {
                                    MyListScreen(
                                        isScreenActive = (currentScreen == TvScreen.MYLIST),
                                        isNavFocused = isNavFocused,
                                        lastSelectedAnime = selectedAnime,
                                        onAnimeSelect = { anime ->
                                            selectedAnime = anime
                                            pushScreen(TvScreen.DETAIL)
                                        },
                                        firstCardFocusRequester = myListFirstCardFocusRequester,
                                        navFocusRequester = myListNavFocusRequester,
                                        onFocusItem = { lastFocusedRequester = it }
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // Detail Screen Overlay
                AnimatedContent(
                    targetState = if (currentScreen == TvScreen.DETAIL) selectedAnime else null,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (targetState != null) {
                            fadeIn(tween(400, delayMillis = 300, easing = LinearOutSlowInEasing)) togetherWith
                                    fadeOut(tween(350, easing = FastOutLinearInEasing))
                        } else {
                            fadeIn(tween(300, easing = LinearOutSlowInEasing)) togetherWith
                                    fadeOut(tween(350, easing = FastOutLinearInEasing))
                        }
                    },
                    label = "detail_transition"
                ) { detailAnime ->
                    if (detailAnime != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties {
                                    canFocus = currentScreen == TvScreen.DETAIL
                                }
                        ) {
                            DetailScreen(
                                anime = detailAnime,
                                isScreenActive = (currentScreen == TvScreen.DETAIL),
                                onBack = { popBackStack() },
                                onPlayEpisode = { episode ->
                                    selectedEpisode = episode
                                    pushScreen(TvScreen.PLAYER)
                                },
                                onAnimeSelect = { newAnime ->
                                    selectedAnime = newAnime
                                }
                            )
                        }
                    }
                }

                // Player Screen Overlay
                AnimatedContent(
                    targetState = (currentScreen == TvScreen.PLAYER),
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(400, delayMillis = 300, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(tween(350, easing = FastOutLinearInEasing))
                    },
                    label = "player_transition"
                ) { isPlayerActive ->
                    val playerEpItem = selectedEpisode
                    val playerAnime = selectedAnime
                    if (isPlayerActive && playerEpItem != null && playerAnime != null) {
                        val animeSlug = playerAnime.slug
                        val currentEpNum = playerEpItem.number

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties {
                                    canFocus = currentScreen == TvScreen.PLAYER
                                }
                        ) {
                            PlayerScreen(
                                anime = playerAnime,
                                episode = playerEpItem,
                                onBack = { popBackStack() },
                                onGoToDetail = {
                                    if (screenStack.contains(TvScreen.DETAIL)) {
                                        popBackStack()
                                    } else {
                                        pushScreen(TvScreen.DETAIL)
                                    }
                                },
                                onSelectEpisode = { newEpisode ->
                                    selectedEpisode = newEpisode
                                },
                                onNextEpisode = {
                                    val nextEpNum = currentEpNum + 1
                                    selectedEpisode = EpisodeItem(
                                        id = "$animeSlug-episode-$nextEpNum",
                                        number = nextEpNum,
                                        title = "Episodio $nextEpNum",
                                        slug = "$animeSlug/$nextEpNum"
                                    )
                                },
                                onPreviousEpisode = if (currentEpNum > 1) {
                                    {
                                        val prevEpNum = currentEpNum - 1
                                        selectedEpisode = EpisodeItem(
                                            id = "$animeSlug-episode-$prevEpNum",
                                            number = prevEpNum,
                                            title = "Episodio $prevEpNum",
                                            slug = "$animeSlug/$prevEpNum"
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            if (showTopNav) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .background(NetflixBlack)
                        .align(Alignment.TopCenter)
                        .zIndex(10f)
                )
                NetflixTopNav(
                    selectedId = when (currentScreen) {
                        TvScreen.SEARCH -> "search"
                        TvScreen.SERIES -> "series"
                        TvScreen.PELICULAS -> "peliculas"
                        TvScreen.KDRAMAS -> "kdramas"
                        TvScreen.MYLIST -> "my_list"
                        else -> "home"
                    },
                    onSelect = { navId ->
                        selectedAnime = null
                        lastFocusedRequester = null
                        val targetScreen = when (navId) {
                            "search" -> TvScreen.SEARCH
                            "home" -> TvScreen.HOME
                            "series" -> TvScreen.SERIES
                            "peliculas" -> TvScreen.PELICULAS
                            "kdramas" -> TvScreen.KDRAMAS
                            "my_list", "mylist" -> TvScreen.MYLIST
                            else -> TvScreen.HOME
                        }
                        if (currentScreen != targetScreen) {
                            screenStack.clear()
                            screenStack.add(targetScreen)
                        }
                    },
                    searchFocusRequester = searchNavFocusRequester,
                    homeFocusRequester = homeNavFocusRequester,
                    myListFocusRequester = myListNavFocusRequester,
                    navFocusRequesters = navFocusRequesters,
                    onFocusChanged = { isNavFocused = it },
                    onDownDpad = {
                        val fallback = when (currentScreen) {
                            TvScreen.HOME -> homePlayButtonFocusRequester
                            TvScreen.SEARCH -> searchFirstKeyFocusRequester
                            TvScreen.SERIES -> seriesFirstKeyFocusRequester
                            TvScreen.PELICULAS -> peliculasFirstKeyFocusRequester
                            TvScreen.KDRAMAS -> kdramasFirstKeyFocusRequester
                            TvScreen.MYLIST -> myListFirstCardFocusRequester
                            else -> null
                        }
                        var focusedSuccessfully = false
                        if (lastFocusedRequester != null) {
                            try {
                                lastFocusedRequester?.requestFocus()
                                focusedSuccessfully = true
                            } catch (_: Exception) {
                                focusedSuccessfully = false
                            }
                        }
                        if (!focusedSuccessfully) {
                            try {
                                fallback?.requestFocus()
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(11f)
                )
            }
        }
    }
}
