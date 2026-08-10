package com.animetv.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.animetv.app.data.model.AnimeItem

@Composable
fun DonghuaScreen(
    isScreenActive: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {},
) = CategoryHubScreen(
    category = "donghua",
    badge = "D O N G H U A",
    isScreenActive = isScreenActive,
    lastSelectedAnime = lastSelectedAnime,
    onAnimeSelect = onAnimeSelect,
    firstKeyFocusRequester = firstKeyFocusRequester,
    navFocusRequester = navFocusRequester,
    onFocusItem = onFocusItem,
)
