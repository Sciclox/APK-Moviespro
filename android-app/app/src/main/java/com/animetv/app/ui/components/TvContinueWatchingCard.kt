package com.animetv.app.ui.components

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
import com.animetv.app.ui.theme.NetflixRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvContinueWatchingCard(
    anime: AnimeItem,
    episodeNumber: Int,
    progress: Float, // 0.0 to 1.0
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(260.dp)
            .height(160.dp)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(6.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f), // No scale
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(6.dp)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 16:9 landscape thumbnail
            AsyncImage(
                model = anime.backdrop ?: anime.image,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Title + Episode info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 14.dp)
            ) {
                Text(
                    text = anime.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "EP $episodeNumber",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium
                )
            }

            // Red progress bar at the very bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.DarkGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .background(NetflixRed)
                )
            }
        }
    }
}
