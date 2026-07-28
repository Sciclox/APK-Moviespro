package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.NetflixLogo
import com.animetv.app.ui.components.NetflixSideNav
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    heroList: List<AnimeItem>,
    catalogList: List<AnimeItem>,
    releasingList: List<AnimeItem>,
    isLoading: Boolean,
    onAnimeSelect: (AnimeItem) -> Unit,
    onNavigateNav: (String) -> Unit = {},
    navFocusRequester: FocusRequester,
    searchNavFocusRequester: FocusRequester,
    myListNavFocusRequester: FocusRequester
) {
    var currentNav by remember { mutableStateOf("home") }
    val playButtonFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Land initial focus on the "Inicio" (Home) icon in SideNav upon app launch
    LaunchedEffect(Unit) {
        delay(200)
        try {
            navFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Outer Box: Content extends 100% full width, SideNav overlays on top
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        if (isLoading) {
            SkeletonHomeScreen()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                // HERO BANNER - 100% Screen Width edge-to-edge behind SideNav
                item {
                    if (heroList.isNotEmpty()) {
                        val firstHero = heroList.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                        ) {
                            // Full width backdrop image (reaches left screen edge)
                            AsyncImage(
                                model = firstHero.backdrop ?: firstHero.image,
                                contentDescription = firstHero.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Left horizontal gradient (stronger black on left behind navbar)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.95f),
                                                Color.Black.copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Bottom vertical gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                NetflixBlack.copy(alpha = 0.4f),
                                                NetflixBlack
                                            )
                                        )
                                    )
                            )

                            // Text Content and Info
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 72.dp, bottom = 24.dp) // shifted 72.dp to avoid overlap with SideNav
                                    .width(480.dp)
                            ) {
                                // Netflix original tag
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NetflixLogo(modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "A N I M E   O R I G I N A L",
                                        color = NetflixLightGrey,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = Inter
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Title in Bebas Neue
                                Text(
                                    text = firstHero.title,
                                    color = Color.White,
                                    fontSize = 48.sp,
                                    fontFamily = BebasNeue,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 50.sp,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = firstHero.synopsis ?: "Disfruta de este increíble anime en calidad Full HD.",
                                    color = NetflixLightGrey,
                                    fontSize = 14.sp,
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 2,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Focusable TV Action Buttons with unified Netflix TV style
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // REPRODUCIR Button
                                    Button(
                                        onClick = { onAnimeSelect(firstHero) },
                                        modifier = Modifier
                                            .focusRequester(playButtonFocusRequester)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    scope.launch {
                                                        delay(60)
                                                        listState.animateScrollToItem(0, 0)
                                                    }
                                                }
                                            }
                                            .onKeyEvent { keyEvent ->
                                                val keyCode = keyEvent.nativeKeyEvent.keyCode
                                                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        navFocusRequester.requestFocus()
                                                    }
                                                    true // Consume both KeyDown and KeyUp
                                                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                    true // Block D-Pad Up focus escape
                                                } else {
                                                    false
                                                }
                                            },
                                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.25f),
                                            contentColor = Color.White,
                                            focusedContainerColor = Color.White,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            focusedBorder = Border(
                                                border = BorderStroke(2.dp, Color.White),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text("▶  REPRODUCIR", fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    }

                                    // MAS INFORMACION Button
                                    Button(
                                        onClick = { onAnimeSelect(firstHero) },
                                        modifier = Modifier
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    scope.launch {
                                                        delay(60)
                                                        listState.animateScrollToItem(0, 0)
                                                    }
                                                }
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                    true // Block D-Pad Up focus escape
                                                } else {
                                                    false
                                                }
                                            },
                                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.25f),
                                            contentColor = Color.White,
                                            focusedContainerColor = Color.White,
                                            focusedContentColor = Color.Black
                                        ),
                                        border = ButtonDefaults.border(
                                            focusedBorder = Border(
                                                border = BorderStroke(2.dp, Color.White),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text("ⓘ  MÁS INFORMACIÓN", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ROW 1: Nuevos lanzamientos (En emisión)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Nuevos lanzamientos (En emisión)",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                        )
                        val releasingRowItems = if (releasingList.isNotEmpty()) releasingList else catalogList
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 72.dp, top = 16.dp, bottom = 24.dp, end = 36.dp)
                        ) {
                            items(releasingRowItems) { anime ->
                                val isFirst = anime == releasingRowItems.firstOrNull()
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

                // ROW 2: Popular en Anime TV
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Popular en Anime TV",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 72.dp, top = 16.dp, bottom = 24.dp, end = 36.dp)
                        ) {
                            items(heroList.reversed()) { anime ->
                                val isFirst = anime == heroList.reversed().firstOrNull()
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

                // ROW 3: Anime de accion
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Anime de accion",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 72.dp, top = 16.dp, bottom = 24.dp, end = 36.dp)
                        ) {
                            items(catalogList.reversed()) { anime ->
                                val isFirst = anime == catalogList.reversed().firstOrNull()
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

        // Overlay Side Navbar
        NetflixSideNav(
            selectedId = currentNav,
            onSelect = { navId ->
                currentNav = navId
                onNavigateNav(navId)
            },
            onRequestExitNav = {
                try {
                    playButtonFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            searchFocusRequester = searchNavFocusRequester,
            homeFocusRequester = navFocusRequester,
            myListFocusRequester = myListNavFocusRequester,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

@Composable
fun SkeletonHomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 72.dp, top = 36.dp)
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .width(420.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}
