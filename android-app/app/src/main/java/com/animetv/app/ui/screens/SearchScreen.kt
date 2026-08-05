package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvKeyboard
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.animetv.app.data.repository.SearchHistoryManager
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    isScreenActive: Boolean = false,
    isNavFocused: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    savedQuery: String = "",
    savedResults: List<AnimeItem> = emptyList(),
    onSearchStateChange: (String, List<AnimeItem>) -> Unit = { _, _ -> },
    onAnimeSelect: (AnimeItem) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf(savedQuery) }
    var searchResults by remember { mutableStateOf<List<AnimeItem>>(savedResults) }
    
    // Directorio / Catalog State
    var selectedGenreSlug by remember { mutableStateOf("") }
    var selectedGenreLabel by remember { mutableStateOf("Todos") }
    var catalogPage by remember { mutableIntStateOf(1) }
    val catalogList = remember { mutableStateListOf<AnimeItem>() }
    var isLoadingCatalog by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    val cleanSearchResults = remember(searchResults) {
        searchResults.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) }
    }
    val cleanCatalogList = remember(catalogList.size) {
        catalogList.filter { !it.slug.contains("perfil", ignoreCase = true) && !it.title.contains("perfil", ignoreCase = true) }
    }

    val scope = rememberCoroutineScope()
    val firstCardFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val genreCategories = remember {
        listOf(
            "tipo:animes" to "Animes",
            "tipo:peliculas" to "Películas",
            "tipo:especiales" to "Especiales",
            "tipo:ovas" to "OVAs",
            "tipo:onas" to "ONAs",
            "accion" to "Acción",
            "aventura" to "Aventura",
            "artes-marciales" to "Artes Marciales",
            "autos" to "Autos",
            "colegial" to "Colegial",
            "comedia" to "Comedia",
            "cosas-de-la-vida" to "Cosas de la vida",
            "deportes" to "Deportes",
            "demonios" to "Demonios",
            "dementia" to "Dementia",
            "drama" to "Drama",
            "ecchi" to "Ecchi",
            "latino" to "Español Latino",
            "fantasia" to "Fantasía",
            "historico" to "Histórico",
            "isekai" to "Isekai",
            "juegos" to "Juegos",
            "josei" to "Josei",
            "magia" to "Magia",
            "mecha" to "Mecha",
            "militar" to "Militar",
            "misterio" to "Misterio",
            "musica" to "Música",
            "nios" to "Niños",
            "parodia" to "Parodia",
            "policial" to "Policial",
            "psicologico" to "Psicológico",
            "romance" to "Romance",
            "samurai" to "Samurai",
            "sci-fi" to "Sci-Fi",
            "seinen" to "Seinen",
            "shoujo" to "Shoujo",
            "shounen" to "Shounen",
            "sobrenatural" to "Sobrenatural",
            "super-poderes" to "Super Poderes",
            "terror" to "Terror",
            "thriller" to "Thriller",
            "vampiros" to "Vampiros"
        )
    }

    // Initial Load or Genre Filter Change (JKAnime Directorio Page 1)
    LaunchedEffect(selectedGenreSlug, searchQuery) {
        if (searchQuery.isBlank()) {
            catalogPage = 1
            hasMorePages = true
            isLoadingCatalog = true
            catalogList.clear()
            try {
                val genreParam = if (!selectedGenreSlug.startsWith("tipo:")) selectedGenreSlug else null
                val tipoParam = if (selectedGenreSlug.startsWith("tipo:")) selectedGenreSlug.removePrefix("tipo:") else null
                val res = ApiClient.apiService.getCatalog(genre = genreParam, tipo = tipoParam, page = 1)
                if (res.success && res.data.isNotEmpty()) {
                    catalogList.clear()
                    catalogList.addAll(res.data)
                } else {
                    hasMorePages = false
                }
            } catch (_: Exception) {
            } finally {
                isLoadingCatalog = false
            }
        }
    }

    // Infinite Scroll Pagination (Auto-fetch next page as user scrolls down)
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItemIndex, catalogList.size) {
        if (searchQuery.isBlank() && !isLoadingCatalog && hasMorePages && catalogList.isNotEmpty()) {
            val totalRows = (cleanCatalogList.size + 3) / 4
            if (firstVisibleItemIndex >= totalRows - 5) {
                isLoadingCatalog = true
                val nextPage = catalogPage + 1
                scope.launch {
                    try {
                        val genreParam = if (!selectedGenreSlug.startsWith("tipo:")) selectedGenreSlug else null
                        val tipoParam = if (selectedGenreSlug.startsWith("tipo:")) selectedGenreSlug.removePrefix("tipo:") else null
                        val res = ApiClient.apiService.getCatalog(genre = genreParam, tipo = tipoParam, page = nextPage)
                        if (res.success && res.data.isNotEmpty()) {
                            catalogPage = nextPage
                            val newUniqueItems = res.data.filter { newItem -> catalogList.none { it.id == newItem.id } }
                            if (newUniqueItems.isNotEmpty()) {
                                catalogList.addAll(newUniqueItems)
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

    // Debounced text search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            delay(400)
            try {
                val res = ApiClient.apiService.searchAnime(searchQuery)
                if (res.success) {
                    searchResults = res.data
                    onSearchStateChange(searchQuery, res.data)
                    if (res.data.isNotEmpty()) {
                        SearchHistoryManager.addQuery(context, searchQuery)
                    }
                }
            } catch (_: Exception) {
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            onSearchStateChange("", emptyList())
            isSearching = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 40.dp, top = 76.dp, end = 40.dp)
        ) {
            // LEFT: Keyboard and Categories
            LazyColumn(
                modifier = Modifier
                    .width(224.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                item {
                    TvKeyboard(
                        onKeyClick = { searchQuery += it },
                        onBackspace = { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                        onClear = { searchQuery = "" },
                        firstKeyFocusRequester = firstKeyFocusRequester,
                        navFocusRequester = navFocusRequester,
                        onRightDpad = {
                            try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
                        },
                        modifier = Modifier.onFocusChanged { focusState ->
                            if (focusState.hasFocus && selectedGenreSlug.isNotEmpty()) {
                                selectedGenreSlug = ""
                                selectedGenreLabel = "Todos"
                            }
                        }
                    )
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genreCategories.forEach { (slug, label) ->
                            val isSelected = (searchQuery.isBlank() && selectedGenreSlug == slug)
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedGenreSlug = slug
                                    selectedGenreLabel = label
                                    scope.launch {
                                        try { listState.scrollToItem(0) } catch (_: Exception) {}
                                    }
                                },
                                scale = ButtonDefaults.scale(focusedScale = 1.05f),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSelected) NetflixRed else Color.Transparent,
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && selectedGenreSlug != slug) {
                                            searchQuery = ""
                                            selectedGenreSlug = slug
                                            selectedGenreLabel = label
                                            scope.launch {
                                                try { listState.scrollToItem(0) } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                                        ) {
                                            try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        } else false
                                    },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontFamily = Inter,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // RIGHT: Directorio Results / Infinite Scroll Grid
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // Recent Searches Row
                val recentQueries = remember(searchQuery) {
                    SearchHistoryManager.getHistory(context)
                }
                if (searchQuery.isBlank() && recentQueries.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(
                                text = "BÚSQUEDAS RECIENTES",
                                color = NetflixLightGrey,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Inter
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(items = recentQueries, key = { it }) { queryText ->
                                    Button(
                                        onClick = {
                                            searchQuery = queryText
                                        },
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.1f),
                                            contentColor = Color.White,
                                            focusedContainerColor = Color.White,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(queryText, fontSize = 12.sp, fontFamily = Inter)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Resultados para \"$searchQuery\""
                            selectedGenreSlug.isNotBlank() -> selectedGenreLabel
                            else -> "Todos los Animes"
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = BebasNeue
                    )
                }

                if (isSearching || (searchQuery.isBlank() && isLoadingCatalog && catalogList.isEmpty())) {
                    items(2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(4) {
                                ShimmerBox(
                                    width = 140.dp,
                                    height = 210.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    val list = if (searchQuery.isBlank()) cleanCatalogList else cleanSearchResults
                    if (list.isEmpty()) {
                        item { Text("No se encontraron resultados.", color = NetflixLightGrey, fontSize = 14.sp) }
                    } else {
                        val rows = list.chunked(4)
                        itemsIndexed(items = rows, key = { _, row -> row.first().id }) { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEachIndexed { colIndex, anime ->
                                    val isFirst = (rowIndex == 0 && colIndex == 0)
                                    val cardFocusRequester = remember(anime.id) {
                                        if (isFirst) firstCardFocusRequester else FocusRequester()
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
                                                if (keyEvent.type == KeyEventType.KeyDown && 
                                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && 
                                                    colIndex == 0) {
                                                    try { firstKeyFocusRequester.requestFocus() } catch (_: Exception) {}
                                                    true
                                                } else false
                                            }
                                    )
                                }
                                if (rowItems.size < 4) {
                                    repeat(4 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }

                        // Loading Indicator at the bottom of the grid during infinite pagination
                        if (searchQuery.isBlank() && isLoadingCatalog && catalogList.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Cargando más animes del directorio...",
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
}
