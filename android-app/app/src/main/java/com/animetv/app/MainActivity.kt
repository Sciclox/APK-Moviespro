package com.animetv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.EpisodeItem
import com.animetv.app.ui.screens.DetailScreen
import com.animetv.app.ui.screens.HomeScreen
import com.animetv.app.ui.screens.MyListScreen
import com.animetv.app.ui.screens.PlayerScreen
import com.animetv.app.ui.screens.SearchScreen
import com.animetv.app.ui.screens.SplashScreen
import com.animetv.app.ui.theme.AnimeTvTheme

enum class TvScreen {
    SPLASH,
    HOME,
    SEARCH,
    MYLIST,
    DETAIL,
    PLAYER
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimeTvTheme {
                val screenStack = remember { mutableStateListOf(TvScreen.SPLASH) }
                val currentScreen = screenStack.lastOrNull() ?: TvScreen.HOME
                var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
                var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }

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

                // Global BackHandler intercepting TV remote BACK key
                BackHandler(enabled = screenStack.size > 1 && currentScreen != TvScreen.SPLASH) {
                    popBackStack()
                }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        when {
                            targetState == TvScreen.HOME && initialState == TvScreen.SPLASH -> {
                                fadeIn(tween(500)) togetherWith fadeOut(tween(300))
                            }
                            targetState == TvScreen.SEARCH || targetState == TvScreen.MYLIST -> {
                                (fadeIn(tween(350)) + slideInHorizontally(tween(350)) { -it / 4 }) togetherWith
                                    (fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 4 })
                            }
                            targetState == TvScreen.DETAIL && (initialState == TvScreen.HOME || initialState == TvScreen.SEARCH || initialState == TvScreen.MYLIST) -> {
                                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 6 }) togetherWith
                                    (fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 6 })
                            }
                            targetState == TvScreen.PLAYER -> {
                                fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                            }
                            targetState == TvScreen.HOME && initialState == TvScreen.DETAIL -> {
                                (fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 6 }) togetherWith
                                    (fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 6 })
                            }
                            targetState == TvScreen.DETAIL && initialState == TvScreen.PLAYER -> {
                                fadeIn(tween(350)) togetherWith fadeOut(tween(250))
                            }
                            else -> {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                            }
                        }
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        TvScreen.SPLASH -> {
                            SplashScreen(
                                onFinished = {
                                    screenStack.clear()
                                    screenStack.add(TvScreen.HOME)
                                }
                            )
                        }

                        TvScreen.HOME -> {
                            HomeScreen(
                                onAnimeSelect = { anime ->
                                    selectedAnime = anime
                                    pushScreen(TvScreen.DETAIL)
                                },
                                onNavigateNav = { navId ->
                                    when (navId) {
                                        "search" -> pushScreen(TvScreen.SEARCH)
                                        "mylist" -> pushScreen(TvScreen.MYLIST)
                                    }
                                }
                            )
                        }

                        TvScreen.SEARCH -> {
                            SearchScreen(
                                onAnimeSelect = { anime ->
                                    selectedAnime = anime
                                    pushScreen(TvScreen.DETAIL)
                                },
                                onNavigateNav = { navId ->
                                    when (navId) {
                                        "home" -> pushScreen(TvScreen.HOME)
                                        "mylist" -> pushScreen(TvScreen.MYLIST)
                                    }
                                }
                            )
                        }

                        TvScreen.MYLIST -> {
                            MyListScreen(
                                onAnimeSelect = { anime ->
                                    selectedAnime = anime
                                    pushScreen(TvScreen.DETAIL)
                                },
                                onNavigateNav = { navId ->
                                    when (navId) {
                                        "home" -> pushScreen(TvScreen.HOME)
                                        "search" -> pushScreen(TvScreen.SEARCH)
                                    }
                                }
                            )
                        }

                        TvScreen.DETAIL -> {
                            selectedAnime?.let { anime ->
                                DetailScreen(
                                    anime = anime,
                                    onBack = {
                                        popBackStack()
                                    },
                                    onPlayEpisode = { episode ->
                                        selectedEpisode = episode
                                        pushScreen(TvScreen.PLAYER)
                                    }
                                )
                            } ?: run {
                                popBackStack()
                            }
                        }

                        TvScreen.PLAYER -> {
                            selectedEpisode?.let { episode ->
                                val currentEpNum = episode.number
                                val animeSlug = selectedAnime?.slug ?: episode.slug.split("/").firstOrNull() ?: "anime"

                                PlayerScreen(
                                    anime = selectedAnime,
                                    episode = episode,
                                    onBack = {
                                        popBackStack()
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
                            } ?: run {
                                popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }
}
