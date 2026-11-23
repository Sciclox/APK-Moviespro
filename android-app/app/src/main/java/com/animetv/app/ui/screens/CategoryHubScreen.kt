package com.animetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvHeroBanner
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.viewmodel.CategoryHubViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryHubScreen(
    category: String,
    badge: String,
    isScreenActive: Boolean,
    lastSelectedAnime: AnimeItem?,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit,
) {
    val viewModel: CategoryHubViewModel = viewModel(key = "category-hub-$category")
    val state by viewModel.uiState.collectAsState()
    val listState = rememberTvLazyListState()

    LaunchedEffect(category) { viewModel.load(category) }
    LaunchedEffect(isScreenActive, lastSelectedAnime) {
        if (isScreenActive && lastSelectedAnime != null && state.hero.any { it.id == lastSelectedAnime.id }) {
            firstKeyFocusRequester.requestFocus()
        }
    }

    Box(Modifier.fillMaxSize().background(NetflixBlack)) {
        TvLazyColumn(
            state = listState,
            pivotOffsets = PivotOffsets(parentFraction = 0.24f),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 80.dp, bottom = 60.dp),
        ) {
            item(key = "hero") {
                when {
                    state.hero.isNotEmpty() -> TvHeroBanner(
                        heroList = state.hero,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        firstKeyFocusRequester = firstKeyFocusRequester,
                        navFocusRequester = navFocusRequester,
                        scrollToRow = {},
                        categoryBadgeText = badge
                    )
                    state.isLoading -> Box(Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 36.dp)) {
                        ShimmerBox(width = 1200.dp, height = 380.dp)
                    }
                    else -> HubMessage(state.error ?: "No hay contenido disponible")
                }
            }

            state.sections.forEachIndexed { sectionIndex, section ->
                item(key = section.slug) {
                    com.animetv.app.ui.components.PaginatedGenreRow(
                        title = section.title,
                        genreKey = section.slug,
                        categoriaKey = category,
                        initialItems = section.items,
                        isScreenActive = isScreenActive,
                        lastSelectedAnime = lastSelectedAnime,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem
                    )
                }
            }
        }
    }
}

@Composable
private fun HubMessage(message: String) {
    Row(Modifier.fillMaxWidth().padding(36.dp)) {
        Text(text = message, color = Color.White, fontFamily = Inter, fontSize = 16.sp)
    }
}
