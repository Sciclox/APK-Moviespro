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
import coil.compose.AsyncImage
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
            .width(160.dp)
            .height(230.dp)
            .padding(vertical = 4.dp, horizontal = 2.dp)
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
            // Poster image with rounded clip
            AsyncImage(
                model = anime.image,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            )

            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Bottom content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                // Title
                Text(
                    text = anime.title,
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
