package com.animetv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvHeroBanner
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.viewmodel.CategoryHubViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()
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

    LaunchedEffect(category) { viewModel.load(category) }
    LaunchedEffect(isScreenActive, lastSelectedAnime) {
        if (isScreenActive && lastSelectedAnime != null && state.hero.any { it.id == lastSelectedAnime.id }) {
            firstKeyFocusRequester.requestFocus()
        }
    }

    Box(Modifier.fillMaxSize().background(NetflixBlack)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 70.dp, bottom = 60.dp),
        ) {
            item(key = "hero") {
                when {
                    state.hero.isNotEmpty() -> TvHeroBanner(
                        heroList = state.hero,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        firstKeyFocusRequester = firstKeyFocusRequester,
                        navFocusRequester = navFocusRequester,
                        scrollToRow = { targetFocusedRowIndex = 0 },
                        categoryBadgeText = badge,
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
                        initialItems = section.items,
                        isScreenActive = isScreenActive,
                        lastSelectedAnime = lastSelectedAnime,
                        rowIndex = sectionIndex + 1,
                        onAnimeSelect = onAnimeSelect,
                        onFocusItem = onFocusItem,
                        onFocusedRowChanged = { targetFocusedRowIndex = it }
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
