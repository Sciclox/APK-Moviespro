package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.NetflixLogo
import com.animetv.app.ui.components.NetflixSideNav
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvKeyboard
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixDarkGrey
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onAnimeSelect: (AnimeItem) -> Unit,
    onNavigateNav: (String) -> Unit,
    navFocusRequester: FocusRequester,
    homeNavFocusRequester: FocusRequester,
    myListNavFocusRequester: FocusRequester
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var popularList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Land initial focus on the "Buscar" (Search) icon in SideNav upon opening SearchScreen (Option B)
    LaunchedEffect(Unit) {
        delay(150)
        try {
            navFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fetch initial popular recommendations for empty state
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = ApiClient.apiService.getCatalog("Accion")
                if (res.success && res.data.isNotEmpty()) {
                    popularList = res.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Real-time debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            delay(300) // 300ms debouncing
            try {
                val res = ApiClient.apiService.searchAnime(searchQuery)
                if (res.success && res.data != null) {
                    searchResults = res.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 72.dp, top = 28.dp, end = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Logo & Title
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NetflixLogo()
                    Text(
                        text = "BUSCAR ANIME",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = BebasNeue,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Search Bar Input Field Display
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "Escribe el nombre de un anime..." else searchQuery,
                            color = if (searchQuery.isEmpty()) NetflixLightGrey else Color.White,
                            fontSize = 16.sp,
                            fontFamily = Inter,
                            fontWeight = if (searchQuery.isEmpty()) FontWeight.Normal else FontWeight.Bold
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text(" ❘", color = NetflixRed, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // On-screen TV Keyboard (D-Pad Navigable)
            item {
                TvKeyboard(
                    onKeyClick = { key -> searchQuery += key },
                    onBackspace = { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                    onClear = { searchQuery = "" },
                    firstKeyFocusRequester = firstKeyFocusRequester,
                    navFocusRequester = navFocusRequester
                )
            }

            // Search Results Section
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (searchQuery.isBlank()) "Búsquedas populares en Anime TV" else "Resultados para \"$searchQuery\"",
                        color = NetflixLightGrey,
                        fontSize = 16.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (isSearching) {
                        Text("Buscando animes...", color = Color.White, fontSize = 14.sp, fontFamily = Inter)
                    } else {
                        val displayList = if (searchQuery.isBlank()) popularList else searchResults

                        if (displayList.isEmpty() && searchQuery.isNotBlank()) {
                            Text(
                                text = "No se encontraron animes para \"$searchQuery\". Intenta con otra palabra.",
                                color = NetflixLightGrey,
                                fontSize = 14.sp,
                                fontFamily = Inter
                            )
                        } else {
                            // TvLazyRow with explicit unclipped layer to allow focus expansion
                            TvLazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(330.dp)
                                    .graphicsLayer { clip = false },
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, end = 36.dp)
                            ) {
                                items(displayList) { anime ->
                                    val isFirst = anime == displayList.firstOrNull()
                                    TvAnimeCard(
                                        anime = anime,
                                        onClick = { onAnimeSelect(anime) },
                                        modifier = if (isFirst) {
                                            Modifier.onKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        navFocusRequester.requestFocus()
                                                    }
                                                    true // Consume both KeyDown and KeyUp
                                                } else {
                                                    false
                                                }
                                            }
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay Side Navigation Bar
        NetflixSideNav(
            selectedId = "search",
            onSelect = onNavigateNav,
            onRequestExitNav = {
                try {
                    firstKeyFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            searchFocusRequester = navFocusRequester,
            homeFocusRequester = homeNavFocusRequester,
            myListFocusRequester = myListNavFocusRequester,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
