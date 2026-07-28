package com.animetv.app.ui.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.components.NetflixLogo
import com.animetv.app.ui.components.NetflixSideNav
import com.animetv.app.ui.components.SkeletonHomeScreen
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.components.TvContinueWatchingCard
import com.animetv.app.ui.components.TvTop10Card
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
    onNavigateNav: (String) -> Unit = {}
) {
    var currentNav by remember { mutableStateOf("home") }
    val playButtonFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Auto-focus on REPRODUCIR button when screen finishes loading
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

                            // Bottom vertical gradient blend
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                NetflixBlack
                                            )
                                        )
                                    )
                            )

                            // Top logo aligned start = 72dp to clear the side nav
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 72.dp, top = 20.dp)
                            ) {
                                NetflixLogo()
                            }

                            // Hero Text Content aligned start = 72dp
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 72.dp, bottom = 48.dp)
                                    .width(460.dp)
                            ) {
                                // N SERIE Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("N", color = NetflixRed, fontSize = 22.sp, fontFamily = BebasNeue, fontWeight = FontWeight.Normal)
                                    Text("SERIE", color = NetflixLightGrey, fontSize = 11.sp, fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
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
                                    // REPRODUCIR Button (scrolls to top when focused)
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

                                    // MAS INFORMACION Button (scrolls to top when focused)
                                    Button(
                                        onClick = { onAnimeSelect(firstHero) },
                                        modifier = Modifier.onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                scope.launch {
                                                    delay(60)
                                                    listState.animateScrollToItem(0, 0)
                                                }
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
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Text("ⓘ  MÁS INFORMACIÓN", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ROW: Seguir viendo
                item {
                    if (heroList.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Seguir viendo",
                                color = NetflixLightGrey,
                                fontSize = 15.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                            )
                            TvLazyRow(
                                modifier = Modifier.fillMaxWidth().height(205.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(start = 72.dp, top = 16.dp, bottom = 20.dp, end = 36.dp)
                            ) {
                                itemsIndexed(heroList.take(5)) { index, anime ->
                                    val fakeProgress = listOf(0.35f, 0.72f, 0.15f, 0.88f, 0.5f)
                                    val fakeEpisode = listOf(3, 7, 1, 12, 5)
                                    TvContinueWatchingCard(
                                        anime = anime,
                                        episodeNumber = fakeEpisode[index % fakeEpisode.size],
                                        progress = fakeProgress[index % fakeProgress.size],
                                        onClick = { onAnimeSelect(anime) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ROW: Tendencias ahora
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Tendencias ahora",
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
                            items(heroList) { anime ->
                                TvAnimeCard(anime = anime, onClick = { onAnimeSelect(anime) })
                            }
                        }
                    }
                }

                // ROW: Top 10 hoy en Anime TV
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Top 10 hoy en Anime TV",
                            color = NetflixLightGrey,
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                        )
                        val top10List = (heroList + catalogList).distinctBy { it.id }.take(10)
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 72.dp, top = 16.dp, bottom = 24.dp, end = 36.dp)
                        ) {
                            itemsIndexed(top10List) { index, anime ->
                                TvTop10Card(anime = anime, rank = index + 1, onClick = { onAnimeSelect(anime) })
                            }
                        }
                    }
                }

                // ROW: Nuevos lanzamientos (Animes en Emisión)
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
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 72.dp, top = 16.dp, bottom = 24.dp, end = 36.dp)
                        ) {
                            items(if (releasingList.isNotEmpty()) releasingList else catalogList) { anime ->
                                TvAnimeCard(anime = anime, onClick = { onAnimeSelect(anime) })
                            }
                        }
                    }
                }

                // ROW: Popular en Anime TV
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
                                TvAnimeCard(anime = anime, onClick = { onAnimeSelect(anime) })
                            }
                        }
                    }
                }

                // ROW: Anime de accion
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
                                TvAnimeCard(anime = anime, onClick = { onAnimeSelect(anime) })
                            }
                        }
                    }
                }
            }
        }

        // Overlay Transparent Lucide Side Navbar positioned on the left
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
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
