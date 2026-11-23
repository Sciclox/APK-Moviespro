package com.animetv.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.animetv.app.data.model.AnimeItem

@Composable
fun SeriesScreen(
    isScreenActive: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {},
) = CategoryHubScreen(
    category = "series",
    badge = "S E R I E S",
    isScreenActive = isScreenActive,
    lastSelectedAnime = lastSelectedAnime,
    onAnimeSelect = onAnimeSelect,
    firstKeyFocusRequester = firstKeyFocusRequester,
    navFocusRequester = navFocusRequester,
    onFocusItem = onFocusItem,
)
