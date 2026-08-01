package com.animetv.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.theme.Inter
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAnimeCard(
    anime: AnimeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var showExpanded by remember { mutableStateOf(false) }

    // Delayed expand: show extra info after 1.5s of focus
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(1500)
            showExpanded = true
        } else {
            showExpanded = false
        }
    }

    val cardShape = RoundedCornerShape(8.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .height(265.dp)
            .padding(0.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = cardShape),
        scale = CardDefaults.scale(focusedScale = 1.0f), // No scale
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = cardShape
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(Color.Black)
        ) {
            // Poster image with rounded clip & downsampled memory target size
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(anime.image)
                    .crossfade(true)
                    .size(300, 450)
                    .build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            )

            // Gradient overlay for contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Top Badges Overlay (Format & Status)
            val formatLabel = remember(anime.type) {
                val rawType = anime.type?.lowercase() ?: ""
                when {
                    rawType.contains("pelicula") || rawType.contains("movie") -> "Película"
                    rawType.contains("ova") -> "OVA"
                    rawType.contains("ona") -> "ONA"
                    rawType.contains("especial") || rawType.contains("special") -> "Especial"
                    else -> "Serie"
                }
            }

            val isEmision = remember(anime.status) {
                val rawStatus = anime.status?.lowercase() ?: ""
                rawStatus.contains("emision") || rawStatus.contains("currently") || rawStatus.contains("releasing")
            }
            val statusLabel = if (isEmision) "En emisión" else "Finalizado"
            val statusBg = if (isEmision) Color(0xFFE50914) else Color(0xFF222222).copy(alpha = 0.85f)

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Format Badge (Serie, Película, Especial, OVA, ONA)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatLabel.uppercase(),
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                }

                // 2. Status Badge (En emisión, Finalizado)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusLabel.uppercase(),
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                }
            }

            val cleanDisplayTitle = remember(anime.title) {
                anime.title
                    .replace(Regex("[\u00a0\u2423\r\n\t_]+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }

            // Bottom content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                // Title
                Text(
                    text = cleanDisplayTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (showExpanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )

                // Expanded info overlay inside card bounds
                AnimatedVisibility(
                    visible = showExpanded,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Match % + Type
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "98% Coincidencia",
                                color = Color(0xFF46D369),
                                fontSize = 10.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = anime.type ?: "Anime",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = Inter,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Mini action buttons
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("PLAY", color = Color.Black, fontSize = 9.sp, fontFamily = Inter, fontWeight = FontWeight.Black)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("INFO", color = Color.White, fontSize = 9.sp, fontFamily = Inter, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHistoryCard(
    historyItem: com.animetv.app.data.model.WatchHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(8.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .height(265.dp)
            .width(190.dp),
        shape = CardDefaults.shape(shape = cardShape),
        scale = CardDefaults.scale(focusedScale = 1.0f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = cardShape
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(Color.Black)
        ) {
            AsyncImage(
                model = historyItem.animeImage.ifBlank { historyItem.backdrop ?: "" },
                contentDescription = historyItem.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Episode Badge (Top Left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(com.animetv.app.ui.theme.NetflixRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "EPISODIO ${historyItem.episodeNumber}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter
                )
            }

            // Bottom Title & Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                val cleanHistoryTitle = remember(historyItem.animeTitle) {
                    historyItem.animeTitle
                        .replace(Regex("[\u00a0\u2423\r\n\t_]+"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
                Text(
                    text = cleanHistoryTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val progress = if (historyItem.durationMs > 0) historyItem.lastPositionMs.toFloat() / historyItem.durationMs.toFloat() else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                            .background(com.animetv.app.ui.theme.NetflixRed)
                    )
                }
            }
        }
    }
}
