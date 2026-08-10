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
            val safeAnimeImage = remember(anime) {
                val raw = anime.image.trim()
                if (raw.isBlank() || raw.contains("animeav1")) {
                    "https://cdn.jkdesa.com/assets/images/animes/image/${anime.slug}.jpg"
                } else {
                    raw
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(safeAnimeImage)
                    .crossfade(true)
                    .size(300, 450)
                    .placeholder(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .fallback(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
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

            // Top Badges Overlay (Relation Header + Format)
            val isRelationCard = remember(anime.status) {
                !anime.status.isNullOrBlank() && !anime.status.contains("emision", ignoreCase = true) && !anime.status.contains("finalizad", ignoreCase = true)
            }

            val primaryBadgeText = remember(anime.status, anime.type) {
                if (isRelationCard) {
                    anime.status!!.trim()
                } else {
                    val rawType = anime.type?.lowercase() ?: ""
                    when {
                        rawType.contains("pelicula") || rawType.contains("movie") -> "Película"
                        rawType.contains("ova") -> "OVA"
                        rawType.contains("ona") -> "ONA"
                        rawType.contains("especial") || rawType.contains("special") -> "Especial"
                        else -> "Serie"
                    }
                }
            }

            val secondaryBadgeText = remember(anime.status, anime.type) {
                if (isRelationCard && !anime.type.isNullOrBlank()) {
                    val rawType = anime.type.lowercase()
                    when {
                        rawType.contains("pelicula") || rawType.contains("movie") -> "Película"
                        rawType.contains("ova") -> "OVA"
                        rawType.contains("especial") || rawType.contains("special") -> "Especial"
                        rawType.contains("serie") -> "Serie"
                        else -> null
                    }
                } else if (!isRelationCard) {
                    val rawStatus = anime.status?.lowercase() ?: ""
                    when {
                        rawStatus.contains("emision") || rawStatus.contains("currently") || rawStatus.contains("releasing") || rawStatus.contains("estreno") -> "En emisión"
                        rawStatus.contains("finalizad") || rawStatus.contains("finished") || rawStatus.contains("concluid") || rawStatus.contains("completed") -> "Finalizado"
                        else -> null
                    }
                } else null
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Primary Badge (Relation or Format)
                val primaryBg = remember(primaryBadgeText, isRelationCard) {
                    val norm = primaryBadgeText.lowercase()
                    if (norm.contains("emisi") || norm.contains("releasing") || norm.contains("estreno")) {
                        Color(0xFF2ECC71)
                    } else if (isRelationCard) {
                        when {
                            norm.contains("secuela") -> Color(0xFFE50914)
                            norm.contains("precuela") -> Color(0xFF007AFF)
                            norm.contains("adicional") -> Color(0xFF8E44AD)
                            norm.contains("personaje") -> Color(0xFF2ECC71)
                            norm.contains("entorno") || norm.contains("alternativo") -> Color(0xFFE67E22)
                            else -> Color(0xFFE50914)
                        }
                    } else {
                        Color.Black.copy(alpha = 0.75f)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = primaryBadgeText.uppercase(),
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                }

                // 2. Secondary Badge (Format or Status)
                if (!secondaryBadgeText.isNullOrBlank()) {
                    val isEnEmision = remember(secondaryBadgeText) {
                        val norm = secondaryBadgeText.lowercase()
                        norm.contains("emisi") || norm.contains("releasing") || norm.contains("estreno")
                    }
                    val secBg = if (isEnEmision) Color(0xFF2ECC71) else Color.Black.copy(alpha = 0.75f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(secBg)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = secondaryBadgeText.uppercase(),
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter
                        )
                    }
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
                Text(
                    text = cleanDisplayTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (isFocused || showExpanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
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
            val safeImage = remember(historyItem) {
                val primary = historyItem.animeImage.ifBlank { historyItem.backdrop ?: "" }
                if (primary.isBlank() || primary.contains("animeav1")) {
                    "https://cdn.jkdesa.com/assets/images/animes/image/${historyItem.animeSlug}.jpg"
                } else {
                    primary
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(safeImage)
                    .crossfade(true)
                    .placeholder(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .fallback(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .build(),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEpisodeCard(
    episodeItem: com.animetv.app.data.model.RecentEpisodeItem,
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
            // Poster image with downsampling for smooth performance
            val safeEpImage = remember(episodeItem) {
                if (episodeItem.image.isBlank() || episodeItem.image.contains("animeav1")) {
                    "https://cdn.jkdesa.com/assets/images/animes/image/${episodeItem.slug}.jpg"
                } else {
                    episodeItem.image
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(safeEpImage)
                    .crossfade(true)
                    .size(300, 450)
                    .placeholder(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .fallback(android.graphics.drawable.ColorDrawable(android.graphics.Color.DKGRAY))
                    .build(),
                contentDescription = episodeItem.title,
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
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Top Badges Overlay: Episode number + Date Tag (Hoy, Ayer, Domingo 02, etc.)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Episode Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF007AFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Ep ${episodeItem.episodeNumber}",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                }

                // 2. Date Tag (Hoy, Ayer, Domingo 02)
                val dateLabel = episodeItem.dateText
                if (!dateLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dateLabel,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Inter
                        )
                    }
                }
            }

            // Bottom Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = episodeItem.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

