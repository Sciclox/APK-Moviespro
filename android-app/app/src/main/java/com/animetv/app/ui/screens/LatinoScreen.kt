package com.animetv.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.animetv.app.data.model.AnimeItem

@Composable
fun LatinoScreen(
    isScreenActive: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {},
) = CategoryHubScreen(
    category = "latino",
    badge = "A N I M E   L A T I N O",
    isScreenActive = isScreenActive,
    lastSelectedAnime = lastSelectedAnime,
    onAnimeSelect = onAnimeSelect,
    firstKeyFocusRequester = firstKeyFocusRequester,
    navFocusRequester = navFocusRequester,
    onFocusItem = onFocusItem,
)
