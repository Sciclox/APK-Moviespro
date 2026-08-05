package com.animetv.app.ui.screens

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.repository.HomeCacheManager
import com.animetv.app.data.repository.MyListManager
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.NetflixRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyListScreen(
    isScreenActive: Boolean = false,
    isNavFocused: Boolean = false,
    lastSelectedAnime: AnimeItem? = null,
    onAnimeSelect: (AnimeItem) -> Unit,
    onNavigateNav: (String) -> Unit = {},
    firstCardFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onFocusItem: (FocusRequester) -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var myListAnimes by remember { mutableStateOf(MyListManager.getSavedAnimes(context)) }
    var selectedFilter by remember { mutableStateOf("Todos") }

    // Read saved playback positions from SharedPreferences
    val prefs = remember { context.getSharedPreferences("player_positions", Context.MODE_PRIVATE) }
    val savedPositionsMap = remember(myListAnimes) {
        val map = mutableMapOf<String, Long>()
        myListAnimes.forEach { anime ->
            val pos = prefs.getLong(anime.slug, 0L)
            if (pos > 5_000L) {
                map[anime.slug] = pos
            }
        }
        map
    }

    // Filtered list based on selected tab chip
    val filteredAnimes = remember(myListAnimes, selectedFilter) {
        when (selectedFilter) {
            "Continuar Viendo" -> myListAnimes.filter { savedPositionsMap.containsKey(it.slug) }
            else -> myListAnimes
        }
    }



    // Fallback/Popular suggestions for empty state
    val popularSuggestions = remember {
        val catalog = HomeCacheManager.getCatalogList(context)
        if (catalog.isNotEmpty()) catalog else HomeCacheManager.getHeroList(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 36.dp, top = 70.dp, end = 36.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Header Title & Count Badge
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MI LISTA DE ANIMES",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = BebasNeue,
                        fontWeight = FontWeight.Normal
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NetflixRed)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${myListAnimes.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Filter Chips (Todos | Continuar Viendo)
            if (myListAnimes.isNotEmpty()) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filters = listOf("Todos", "Continuar Viendo")
                        filters.forEachIndexed { index, filterName ->
                            val isSelected = selectedFilter == filterName
                            val count = if (filterName == "Continuar Viendo") savedPositionsMap.size else myListAnimes.size

                            val chipFocusRequester = remember(filterName) {
                                if (index == 0) firstCardFocusRequester else FocusRequester()
                            }

                            Surface(
                                onClick = { selectedFilter = filterName },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(20.dp))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                modifier = Modifier
                                    .focusRequester(chipFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusItem(chipFocusRequester)
                                        }
                                    }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                            navFocusRequester.requestFocus()
                                            true
                                        } else false
                                    }
                            ) {
                                Text(
                                    text = "$filterName ($count)",
                                    fontSize = 13.sp,
                                    fontFamily = Inter,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Main 5-Column Grid or Cinematic Empty State
            item {
                if (myListAnimes.isEmpty() || filteredAnimes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        // Empty Banner Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF1E1E1E),
                                            Color(0xFF121212)
                                        )
                                    )
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = "Lista Vacía",
                                    tint = NetflixRed,
                                    modifier = Modifier.size(54.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (myListAnimes.isEmpty()) "Aún no has guardado animes en tu lista" else "No hay animes en esta categoría",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontFamily = Inter,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Explora el catálogo o añade sugerencias a continuación para tener tus series a la mano.",
                                        color = NetflixLightGrey,
                                        fontSize = 13.sp,
                                        fontFamily = Inter
                                    )
                                }
                            }
                        }

                        // Popular Anime Suggestions Carousel
                        if (popularSuggestions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "SUGERENCIAS POPULARES PARA TU LISTA",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = BebasNeue
                                )
                                TvLazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(end = 36.dp)
                                ) {
                                    items(popularSuggestions.take(10)) { anime ->
                                        var inList by remember { mutableStateOf(MyListManager.isInList(context, anime.id)) }

                                        Column(
                                            modifier = Modifier.width(175.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val cardFocusRequester = remember(anime.id) { FocusRequester() }
                                            TvAnimeCard(
                                                anime = anime,
                                                onClick = { onAnimeSelect(anime) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(245.dp)
                                                    .focusRequester(cardFocusRequester)
                                                    .onFocusChanged { if (it.isFocused) onFocusItem(cardFocusRequester) }
                                                    .onKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                            navFocusRequester.requestFocus()
                                                            true
                                                        } else false
                                                    }
                                            )
                                            Surface(
                                                onClick = {
                                                    MyListManager.toggleAnime(context, anime)
                                                    inList = MyListManager.isInList(context, anime.id)
                                                    myListAnimes = MyListManager.getSavedAnimes(context)
                                                },
                                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = if (inList) Color.White.copy(alpha = 0.2f) else NetflixRed,
                                                    contentColor = Color.White,
                                                    focusedContainerColor = Color.White,
                                                    focusedContentColor = Color.Black
                                                ),
                                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (inList) Icons.Default.Check else Icons.Default.BookmarkBorder,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (inList) "En Lista" else "+ Lista",
                                                        fontSize = 12.sp,
                                                        fontFamily = Inter,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 5-Column Grid Layout
                    val rows = filteredAnimes.chunked(5)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rows.forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEachIndexed { colIndex, anime ->
                                    val isFirstItemInGrid = (rowIndex == 0 && colIndex == 0)
                                    val hasProgress = savedPositionsMap.containsKey(anime.slug)

                                    val cardFocusRequester = remember(anime.id) {
                                        if (isFirstItemInGrid) firstCardFocusRequester else FocusRequester()
                                    }

                                    LaunchedEffect(isScreenActive, lastSelectedAnime) {
                                        if (isScreenActive && lastSelectedAnime?.id == anime.id) {
                                            kotlinx.coroutines.delay(300)
                                            try {
                                                cardFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(cardFocusRequester)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                                        // Long press / Menu key removes anime from list
                                                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                                            MyListManager.toggleAnime(context, anime)
                                                            myListAnimes = MyListManager.getSavedAnimes(context)
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    ) {
                                        TvAnimeCard(
                                            anime = anime,
                                            onClick = { onAnimeSelect(anime) },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Watch Progress Indicator Bar (Red bottom bar on card)
                                        if (hasProgress) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .align(Alignment.BottomCenter)
                                                    .background(Color.White.copy(alpha = 0.3f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(0.65f)
                                                        .background(NetflixRed)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Fill remaining columns if last row has less than 5 items
                                if (rowItems.size < 5) {
                                    repeat(5 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


