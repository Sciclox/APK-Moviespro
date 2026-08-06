package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.repository.CatalogCacheManager
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixLightGrey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogScreen(
    title: String,
    categoria: String? = null,
    tipo: String? = null,
    genre: String? = null,
    isScreenActive: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var catalogPage by remember { mutableIntStateOf(1) }
    val catalogList = remember { mutableStateListOf<AnimeItem>() }
    var isLoadingCatalog by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }

    val cleanCatalogList = remember(catalogList.toList()) {
        catalogList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) }
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val cacheKey = remember(categoria, tipo, genre) {
        "catalog:${categoria ?: "none"}:${tipo ?: "none"}:${genre ?: "none"}:1"
    }

    // Initial Load with Instant Disk Cache + Network Sync
    LaunchedEffect(categoria, tipo, genre) {
        catalogPage = 1
        hasMorePages = true

        val cachedData = CatalogCacheManager.get(context, cacheKey)
        if (cachedData != null && cachedData.isNotEmpty()) {
            catalogList.clear()
            catalogList.addAll(cachedData)
            isLoadingCatalog = false
        } else {
            isLoadingCatalog = true
        }

        try {
            val res = ApiClient.apiService.getCatalog(
                genre = genre,
                categoria = categoria,
                tipo = tipo,
                page = 1
            )
            if (res.success && res.data.isNotEmpty()) {
                catalogList.clear()
                catalogList.addAll(res.data)
                CatalogCacheManager.put(context, cacheKey, res.data)
            } else if (cachedData.isNullOrEmpty()) {
                hasMorePages = false
            }
        } catch (_: Exception) {
        } finally {
            isLoadingCatalog = false
        }
    }

    // Proactive Infinite Scroll Pagination (pre-fetches next page early at 8 rows from end)
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItemIndex, catalogList.size) {
        if (!isLoadingCatalog && hasMorePages && catalogList.isNotEmpty()) {
            val totalRows = (cleanCatalogList.size + 3) / 4
            if (firstVisibleItemIndex >= totalRows - 8) {
                isLoadingCatalog = true
                val nextPage = catalogPage + 1
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val res = ApiClient.apiService.getCatalog(
                            genre = genre,
                            categoria = categoria,
                            tipo = tipo,
                            page = nextPage
                        )
                        if (res.success && res.data.isNotEmpty()) {
                            catalogPage = nextPage
                            val newUniqueItems = res.data.filter { newItem -> catalogList.none { it.id == newItem.id } }
                            if (newUniqueItems.isNotEmpty()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    catalogList.addAll(newUniqueItems)
                                }
                            } else {
                                hasMorePages = false
                            }
                        } else {
                            hasMorePages = false
                        }
                    } catch (_: Exception) {
                    } finally {
                        isLoadingCatalog = false
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, top = 76.dp, end = 36.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = BebasNeue,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                if (isLoadingCatalog && catalogList.isEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(5) { ShimmerBox(width = 160.dp, height = 230.dp) }
                        }
                    }
                } else if (cleanCatalogList.isEmpty()) {
                    item {
                        Text(
                            text = "No se encontraron animes en esta sección.",
                            color = NetflixLightGrey,
                            fontSize = 14.sp,
                            fontFamily = Inter
                        )
                    }
                } else {
                    val rows = cleanCatalogList.chunked(5)
                    itemsIndexed(rows) { rowIndex, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEachIndexed { colIndex, anime ->
                                val isFirst = (rowIndex == 0 && colIndex == 0)
                                val cardFocusRequester = remember(anime.id) {
                                    if (isFirst) firstKeyFocusRequester else FocusRequester()
                                }

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
                                        .weight(1f)
                                        .focusRequester(cardFocusRequester)
                                        .onFocusChanged { if (it.isFocused) onFocusItem(cardFocusRequester) }
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.nativeKeyEvent.keyCode) {
                                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                                        if (rowIndex == 0) {
                                                            try { navFocusRequester.requestFocus() } catch (_: Exception) {}
                                                            true
                                                        } else false
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                        if (colIndex == 0) {
                                                            try { firstKeyFocusRequester.requestFocus() } catch (_: Exception) {}
                                                            true
                                                        } else false
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                )
                            }
                            if (rowItems.size < 5) {
                                repeat(5 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    if (isLoadingCatalog && catalogList.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cargando más animes...",
                                    color = NetflixLightGrey,
                                    fontSize = 13.sp,
                                    fontFamily = Inter
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
