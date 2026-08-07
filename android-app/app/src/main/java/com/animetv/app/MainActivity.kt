package com.animetv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.EpisodeItem
import com.animetv.app.data.repository.HomeCacheManager
import com.animetv.app.ui.components.NetflixTopNav
import com.animetv.app.ui.screens.CatalogScreen
import com.animetv.app.ui.screens.DetailScreen
import com.animetv.app.ui.screens.DonghuaScreen
import com.animetv.app.ui.screens.HomeScreen
import com.animetv.app.ui.screens.LatinoScreen
import com.animetv.app.ui.screens.MoviesScreen
import com.animetv.app.ui.screens.MyListScreen
import com.animetv.app.ui.screens.PlayerScreen
import com.animetv.app.ui.screens.SearchScreen
import com.animetv.app.ui.screens.SplashScreen
import com.animetv.app.ui.theme.AnimeTvTheme
import com.animetv.app.ui.theme.NetflixBlack

enum class TvScreen {
    SPLASH,
    HOME,
    SEARCH,
    DONGHUA,
    LATINO,
    ANIMES,
    PELICULAS,
    ESPECIALES,
    OVAS,
    ONAS,
    MYLIST,
    DETAIL,
    PLAYER
}

class MainActivity : ComponentActivity() {
    private var currentActivityIntent by mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentActivityIntent = intent
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        currentActivityIntent = intent
        setContent {
            AnimeTvTheme {
                val context = LocalContext.current
                val screenStack = remember { mutableStateListOf(TvScreen.SPLASH) }
                val currentScreen = screenStack.lastOrNull() ?: TvScreen.HOME
                var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
                var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }

                // Deep Link Handler for Android TV 'Seguir viendo' (Watch Next) System Channel
                LaunchedEffect(currentActivityIntent) {
                    val intentUri = currentActivityIntent?.data
                    if (intentUri != null && intentUri.scheme == "animetv" && intentUri.host == "play") {
                        val animeSlug = intentUri.getQueryParameter("slug") ?: ""
                        val epNumStr = intentUri.getQueryParameter("ep") ?: "1"
                        val epNum = epNumStr.toIntOrNull() ?: 1

                        if (animeSlug.isNotBlank()) {
                            val historyMatch = com.animetv.app.data.repository.WatchHistoryManager.getHistory(context)
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

                val donghuaNavFocusRequester = remember { FocusRequester() }
                val latinoNavFocusRequester = remember { FocusRequester() }
                val animesNavFocusRequester = remember { FocusRequester() }
                val peliculasNavFocusRequester = remember { FocusRequester() }
                val especialesNavFocusRequester = remember { FocusRequester() }
                val ovasNavFocusRequester = remember { FocusRequester() }
                val onasNavFocusRequester = remember { FocusRequester() }

                val navFocusRequesters = remember {
                    mapOf(
                        "donghua" to donghuaNavFocusRequester,
                        "latino" to latinoNavFocusRequester,
                        "animes" to animesNavFocusRequester,
                        "peliculas" to peliculasNavFocusRequester,
                        "especiales" to especialesNavFocusRequester,
                        "ovas" to ovasNavFocusRequester,
                        "onas" to onasNavFocusRequester
                    )
                }

                var isNavFocused by remember { mutableStateOf(false) }

                // Content Focus Requesters
                val homePlayButtonFocusRequester = remember { FocusRequester() }
                val searchFirstKeyFocusRequester = remember { FocusRequester() }
                val myListFirstCardFocusRequester = remember { FocusRequester() }

                val donghuaFirstKeyFocusRequester = remember { FocusRequester() }
                val latinoFirstKeyFocusRequester = remember { FocusRequester() }
                val animesFirstKeyFocusRequester = remember { FocusRequester() }
                val peliculasFirstKeyFocusRequester = remember { FocusRequester() }
                val especialesFirstKeyFocusRequester = remember { FocusRequester() }
                val ovasFirstKeyFocusRequester = remember { FocusRequester() }
                val onasFirstKeyFocusRequester = remember { FocusRequester() }

                var lastFocusedRequester by remember { mutableStateOf<FocusRequester?>(null) }

                // Cache state
                val cachedHero = remember { HomeCacheManager.getHeroList(context) }
                val cachedCatalog = remember { HomeCacheManager.getCatalogList(context) }
                val cachedReleasing = remember { HomeCacheManager.getReleasingList(context) }

                var homeHeroList by remember { mutableStateOf<List<AnimeItem>>(cachedHero) }
                var homeCatalogList by remember { mutableStateOf<List<AnimeItem>>(cachedCatalog) }
                var homeReleasingList by remember { mutableStateOf<List<AnimeItem>>(cachedReleasing) }
                var homeLatestEpisodesList by remember { mutableStateOf<List<com.animetv.app.data.model.RecentEpisodeItem>>(emptyList()) }
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

                // API Revalidation
                // Parallel API revalidation — all 4 calls launch simultaneously
                LaunchedEffect(Unit) {
                    try {
                        coroutineScope {
                            val heroDeferred = async { ApiClient.apiService.getHeroBanners() }
                            val catalogDeferred = async { ApiClient.apiService.getCatalog(genre = "Accion") }
                            val releasingDeferred = async { ApiClient.apiService.getLatest() }
                            val latestEpDeferred = async { ApiClient.apiService.getLatestEpisodes() }

                            try {
                                val heroRes = heroDeferred.await()
                                if (heroRes.success && heroRes.data.isNotEmpty()) homeHeroList = heroRes.data
                            } catch (_: Exception) {}

                            try {
                                val catalogRes = catalogDeferred.await()
                                if (catalogRes.success && catalogRes.data.isNotEmpty()) homeCatalogList = catalogRes.data
                            } catch (_: Exception) {}

                            try {
                                val releasingRes = releasingDeferred.await()
                                if (releasingRes.success && releasingRes.data.isNotEmpty()) homeReleasingList = releasingRes.data
                            } catch (_: Exception) {}

                            try {
                                val latestEpRes = latestEpDeferred.await()
                                if (latestEpRes.success && latestEpRes.data.isNotEmpty()) homeLatestEpisodesList = latestEpRes.data
                            } catch (_: Exception) {}
                        }

                        HomeCacheManager.saveHomeData(context, homeHeroList, homeCatalogList, homeReleasingList)
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
                        TvScreen.HOME, TvScreen.SEARCH, TvScreen.LATINO, TvScreen.PELICULAS,
                        TvScreen.DONGHUA, TvScreen.MYLIST
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
                            TvScreen.DONGHUA -> donghuaNavFocusRequester.requestFocus()
                            TvScreen.LATINO -> latinoNavFocusRequester.requestFocus()
                            TvScreen.ANIMES -> animesNavFocusRequester.requestFocus()
                            TvScreen.PELICULAS -> peliculasNavFocusRequester.requestFocus()
                            TvScreen.ESPECIALES -> especialesNavFocusRequester.requestFocus()
                            TvScreen.OVAS -> ovasNavFocusRequester.requestFocus()
                            TvScreen.ONAS -> onasNavFocusRequester.requestFocus()
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
                    val mainTabScreens = remember {
                        setOf(
                            TvScreen.HOME, TvScreen.SEARCH, TvScreen.DONGHUA, TvScreen.LATINO,
                            TvScreen.ANIMES, TvScreen.PELICULAS, TvScreen.ESPECIALES,
                            TvScreen.OVAS, TvScreen.ONAS, TvScreen.MYLIST, TvScreen.SPLASH
                        )
                    }
                    val activeTabScreen = remember(currentScreen, screenStack.size) {
                        screenStack.findLast { it in mainTabScreens } ?: TvScreen.HOME
                    }

                    val isOverlayActive = currentScreen == TvScreen.DETAIL || currentScreen == TvScreen.PLAYER

                    // Global Focus Restoration on returning from DETAIL or PLAYER screens
                    LaunchedEffect(currentScreen) {
                        if (currentScreen in mainTabScreens) {
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
                                        TvScreen.DONGHUA -> {
                                            DonghuaScreen(
                                                isScreenActive = (currentScreen == TvScreen.DONGHUA),
                                                lastSelectedAnime = selectedAnime,
                                                onAnimeSelect = { anime ->
                                                    selectedAnime = anime
                                                    pushScreen(TvScreen.DETAIL)
                                                },
                                                firstKeyFocusRequester = donghuaFirstKeyFocusRequester,
                                                navFocusRequester = donghuaNavFocusRequester,
                                                onFocusItem = { lastFocusedRequester = it }
                                            )
                                        }
                                        TvScreen.LATINO -> {
                                            LatinoScreen(
                                                isScreenActive = (currentScreen == TvScreen.LATINO),
                                                lastSelectedAnime = selectedAnime,
                                                onAnimeSelect = { anime ->
                                                    selectedAnime = anime
                                                    pushScreen(TvScreen.DETAIL)
                                                },
                                                firstKeyFocusRequester = latinoFirstKeyFocusRequester,
                                                navFocusRequester = latinoNavFocusRequester,
                                                onFocusItem = { lastFocusedRequester = it }
                                            )
                                        }
                                        TvScreen.ANIMES -> {
                                            CatalogScreen(
                                                title = "Animes",
                                                tipo = "tv",
                                                isScreenActive = (currentScreen == TvScreen.ANIMES),
                                                lastSelectedAnime = selectedAnime,
                                                onAnimeSelect = { anime ->
                                                    selectedAnime = anime
                                                    pushScreen(TvScreen.DETAIL)
                                                },
                                                firstKeyFocusRequester = animesFirstKeyFocusRequester,
                                                navFocusRequester = animesNavFocusRequester,
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
                                        TvScreen.ESPECIALES -> {
                                            CatalogScreen(
                                                title = "Especiales",
                                                tipo = "special",
                                                isScreenActive = (currentScreen == TvScreen.ESPECIALES),
                                                lastSelectedAnime = selectedAnime,
                                                onAnimeSelect = { anime ->
                                                    selectedAnime = anime
                                                    pushScreen(TvScreen.DETAIL)
                                                },
                                                firstKeyFocusRequester = especialesFirstKeyFocusRequester,
                                                navFocusRequester = especialesNavFocusRequester,
                                                onFocusItem = { lastFocusedRequester = it }
                                            )
                                        }
                                        TvScreen.OVAS -> {
                                            CatalogScreen(
                                                title = "OVAs",
                                                tipo = "ova",
                                                isScreenActive = (currentScreen == TvScreen.OVAS),
                                                lastSelectedAnime = selectedAnime,
                                                onAnimeSelect = { anime ->
                                                    selectedAnime = anime
                                                    pushScreen(TvScreen.DETAIL)
                                                },
                                                firstKeyFocusRequester = ovasFirstKeyFocusRequester,
                                                navFocusRequester = ovasNavFocusRequester,
                                                onFocusItem = { lastFocusedRequester = it }
                                            )
                                        }
                                        TvScreen.ONAS -> {
                                            CatalogScreen(
                                                title = "ONAs",
                                                tipo = "ona",
                                                isScreenActive = (currentScreen == TvScreen.ONAS),
                                                lastSelectedAnime = selectedAnime,
                                                onAnimeSelect = { anime ->
                                                    selectedAnime = anime
                                                    pushScreen(TvScreen.DETAIL)
                                                },
                                                firstKeyFocusRequester = onasFirstKeyFocusRequester,
                                                navFocusRequester = onasNavFocusRequester,
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
                            targetState = if (currentScreen == TvScreen.DETAIL || currentScreen == TvScreen.PLAYER) selectedAnime else null,
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
                            if (isPlayerActive && selectedEpisode != null && selectedAnime != null) {
                                val playerEpItem = selectedEpisode!!
                                val animeSlug = selectedAnime!!.slug
                                val currentEpNum = playerEpItem.number

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusProperties {
                                            canFocus = currentScreen == TvScreen.PLAYER
                                        }
                                ) {
                                    PlayerScreen(
                                        anime = selectedAnime,
                                        episode = playerEpItem,
                                        onBack = { popBackStack() },
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
                                TvScreen.DONGHUA -> "donghua"
                                TvScreen.LATINO -> "latino"
                                TvScreen.ANIMES -> "animes"
                                TvScreen.PELICULAS -> "peliculas"
                                TvScreen.ESPECIALES -> "especiales"
                                TvScreen.OVAS -> "ovas"
                                TvScreen.ONAS -> "onas"
                                TvScreen.MYLIST -> "my_list"
                                else -> "home"
                            },
                            onSelect = { navId ->
                                selectedAnime = null
                                lastFocusedRequester = null
                                val targetScreen = when (navId) {
                                    "search" -> TvScreen.SEARCH
                                    "home" -> TvScreen.HOME
                                    "donghua" -> TvScreen.DONGHUA
                                    "latino" -> TvScreen.LATINO
                                    "animes" -> TvScreen.ANIMES
                                    "peliculas" -> TvScreen.PELICULAS
                                    "especiales" -> TvScreen.ESPECIALES
                                    "ovas" -> TvScreen.OVAS
                                    "onas" -> TvScreen.ONAS
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
                                    TvScreen.DONGHUA -> donghuaFirstKeyFocusRequester
                                    TvScreen.LATINO -> latinoFirstKeyFocusRequester
                                    TvScreen.ANIMES -> animesFirstKeyFocusRequester
                                    TvScreen.PELICULAS -> peliculasFirstKeyFocusRequester
                                    TvScreen.ESPECIALES -> especialesFirstKeyFocusRequester
                                    TvScreen.OVAS -> ovasFirstKeyFocusRequester
                                    TvScreen.ONAS -> onasFirstKeyFocusRequester
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
    }
}
