package com.animetv.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PaginatedGenreRow(
    title: String,
    genreKey: String? = null,
    tipoKey: String? = null,
    initialItems: List<AnimeItem>,
    isScreenActive: Boolean,
    lastSelectedAnime: AnimeItem?,
    rowIndex: Int,
    onAnimeSelect: (AnimeItem) -> Unit,
    onFocusItem: (FocusRequester) -> Unit,
    onFocusedRowChanged: (Int) -> Unit
) {
    val rowListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var itemsList by remember(initialItems) { mutableStateOf(initialItems) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }

    fun loadNextPage() {
        if (!isLoadingMore && canLoadMore && (genreKey != null || tipoKey != null)) {
            isLoadingMore = true
            scope.launch(Dispatchers.IO) {
                try {
                    val nextPage = currentPage + 1
                    val res = ApiClient.apiService.getCatalog(
                        genre = genreKey,
                        tipo = tipoKey,
                        page = nextPage
                    )
                    if (res.success && res.data.isNotEmpty()) {
                        val existingIds = itemsList.map { it.id }.toSet()
                        val newItems = res.data.filter { newItem -> !existingIds.contains(newItem.id) }
                        if (newItems.isNotEmpty()) {
                            currentPage = nextPage
                            withContext(Dispatchers.Main) {
                                itemsList = itemsList + newItems
                            }
                        } else {
                            canLoadMore = false
                        }
                    } else {
                        canLoadMore = false
                    }
                } catch (_: Exception) {
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    val lastVisibleIndex by remember {
        derivedStateOf {
            rowListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
    }

    LaunchedEffect(lastVisibleIndex) {
        if (lastVisibleIndex >= 0 && lastVisibleIndex >= itemsList.size - 5) {
            loadNextPage()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
        )

        LazyRow(
            state = rowListState,
            modifier = Modifier.fillMaxWidth().height(310.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 36.dp, top = 8.dp, bottom = 16.dp, end = 36.dp)
        ) {
            itemsIndexed(items = itemsList, key = { idx, anime -> "$title-${anime.id}-$idx" }) { idx, anime ->
                val cardFocusRequester = remember(anime.id) { FocusRequester() }

                LaunchedEffect(isScreenActive, lastSelectedAnime) {
                    if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                        delay(300)
                        try {
                            cardFocusRequester.requestFocus()
                        } catch (_: Exception) {}
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
                                onFocusedRowChanged(rowIndex)
                                if (idx >= itemsList.size - 6) {
                                    loadNextPage()
                                }
                            }
                        }
                )
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .width(190.dp)
                            .height(280.dp)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ShimmerBox(
                            width = 190.dp,
                            height = 280.dp,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}
