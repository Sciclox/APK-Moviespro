package com.animetv.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeDetail
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.EpisodeItem
import com.animetv.app.data.repository.MyListManager
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvEpisodeCard
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
fun DetailScreen(
    anime: AnimeItem,
    onBack: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit
) {
    val context = LocalContext.current
    var isInMyList by remember(anime.id) { mutableStateOf(MyListManager.isInList(context, anime.id)) }
    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf(1) }
    var expandedSeasonMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backButtonFocusRequester = remember { FocusRequester() }

    val isMovie = remember(detail, anime) {
        val rawType = (detail?.type ?: anime.type ?: "TV").uppercase()
        rawType.contains("MOVIE") || rawType.contains("PELICULA") || rawType.contains("PELÍCULA")
    }

    val fallbackEpisodes = remember(anime, isMovie) {
        if (isMovie) {
            listOf(
                EpisodeItem(
                    id = "${anime.id}-movie",
                    number = 1,
                    title = "Película Completa",
                    image = anime.backdrop ?: anime.image,
                    duration = "1 h 45 min",
                    overview = "Disfruta de la película completa de ${anime.title} en calidad Full HD.",
                    slug = "${anime.slug}/1"
                )
            )
        } else {
            List(12) { i ->
                val epNum = i + 1
                EpisodeItem(
                    id = "${anime.id}-ep-$epNum",
                    number = epNum,
                    title = "Episodio $epNum",
                    image = anime.backdrop ?: anime.image,
                    duration = "24 min",
                    overview = "Capítulo $epNum de la serie animada ${anime.title}.",
                    slug = "${anime.slug}/$epNum"
                )
            }
        }
    }

    LaunchedEffect(anime.slug) {
        scope.launch {
            try {
                val res = ApiClient.apiService.getAnimeDetails(anime.slug)
                if (res.success) {
                    detail = res.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    // Auto focus on ATRÁS button when screen opens
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(150)
            try {
                backButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val allEpisodes = if (detail?.episodes.isNullOrEmpty()) fallbackEpisodes else detail!!.episodes

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        // Hero Backdrop Background
        AsyncImage(
            model = detail?.backdrop ?: anime.backdrop ?: anime.image,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            NetflixBlack.copy(alpha = 0.85f),
                            NetflixBlack
                        ),
                        startY = 0f,
                        endY = 1200f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixBlack.copy(alpha = 0.95f),
                            NetflixBlack.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        if (isLoading) {
            Column(
                modifier = Modifier
                    .padding(start = 72.dp, top = 60.dp)
                    .width(500.dp)
            ) {
                ShimmerBox(width = 100.dp, height = 36.dp)
                Spacer(modifier = Modifier.height(20.dp))
                ShimmerBox(width = 320.dp, height = 40.dp)
                Spacer(modifier = Modifier.height(12.dp))
                ShimmerBox(width = 240.dp, height = 20.dp)
                Spacer(modifier = Modifier.height(16.dp))
                ShimmerBox(width = 500.dp, height = 80.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 72.dp, top = 36.dp, end = 48.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Item 1: ATRÁS Navigation Button as top item in LazyColumn
                item {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.focusRequester(backButtonFocusRequester),
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "←  ATRÁS",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Item 2: Title + Metadata
                item {
                    Column {
                        // N SERIE / N PELÍCULA Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("N", color = NetflixRed, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = BebasNeue)
                            Text(
                                text = if (isMovie) "PELÍCULA" else "SERIE",
                                color = NetflixLightGrey,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontFamily = Inter
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Title
                        Text(
                            text = detail?.title ?: anime.title,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = BebasNeue,
                            lineHeight = 38.sp
                        )

                        if (!detail?.japaneseTitle.isNullOrBlank()) {
                            Text(
                                text = detail?.japaneseTitle ?: "",
                                color = NetflixLightGrey,
                                fontSize = 14.sp,
                                fontFamily = Inter
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("98% Coincidencia", color = Color(0xFF46D369), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                            Text("${detail?.year ?: 2024}", color = NetflixLightGrey, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)

                            val statusText = detail?.status ?: "FINALIZADO"
                            val isReleasing = statusText.contains("EMISIÓN")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isReleasing) Color(0xFF2ECC71).copy(alpha = 0.2f) else NetflixDarkGrey)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isMovie) "PELÍCULA HD" else (if (isReleasing) "EN EMISIÓN" else "FINALIZADO"),
                                    color = if (isReleasing && !isMovie) Color(0xFF2ECC71) else NetflixLightGrey,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Inter
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(NetflixRed)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("FULL HD", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = Inter)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Genre chips
                        val genres = detail?.genres ?: listOf("Acción", "Aventura", "Fantasía")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            genres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2B2B2B))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(genre, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)
                                }
                            }
                        }
                    }
                }

                // Item 3: Synopsis
                item {
                    Text(
                        text = detail?.synopsis ?: anime.synopsis ?: "Sinopsis no disponible.",
                        color = NetflixLightGrey,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = 4,
                        fontFamily = Inter
                    )
                }

                // Item 4: Action Buttons with unified Netflix TV style
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val firstEp = detail?.episodes?.firstOrNull() ?: fallbackEpisodes.first()

                        // REPRODUCIR Button
                        Button(
                            onClick = { onPlayEpisode(firstEp) },
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
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = if (isMovie) "▶  REPRODUCIR PELÍCULA" else "▶  REPRODUCIR EP. 1",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                fontFamily = Inter
                            )
                        }

                        // + MI LISTA / ✓ EN MI LISTA Button
                        Button(
                            onClick = {
                                isInMyList = MyListManager.toggleAnime(context, anime)
                            },
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isInMyList) Color(0xFF2ECC71).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f),
                                contentColor = Color.White,
                                focusedContainerColor = if (isInMyList) Color(0xFF2ECC71) else Color.White,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = if (isInMyList) "✓  EN MI LISTA" else "+  MI LISTA",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = Inter
                            )
                        }
                    }
                }

                // Item 5: Episodios / Película Header
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isMovie) "Película" else "Episodios",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BebasNeue
                    )
                }

                // Item 6: Horizontal Episode List / Movie Item (TvLazyRow)
                item {
                    val displayEpisodes = if (isMovie) {
                        allEpisodes.take(1)
                    } else {
                        allEpisodes
                    }

                    TvLazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp, end = 24.dp)
                    ) {
                        items(displayEpisodes) { ep ->
                            TvEpisodeCard(
                                episode = ep,
                                onClick = { onPlayEpisode(ep) }
                            )
                        }
                    }
                }
            }
        }
    }
}
