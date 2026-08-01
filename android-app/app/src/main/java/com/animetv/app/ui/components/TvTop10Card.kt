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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.NetflixRed

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTop10Card(
    anime: AnimeItem,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(205.dp)
            .height(250.dp)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        colors = CardDefaults.colors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // LAYER 1 (BEHIND): Giant Rank Number 1..10 in horizontal line
            Text(
                text = "$rank",
                color = if (isFocused) NetflixRed else Color.White.copy(alpha = 0.85f),
                fontSize = if (rank == 10) 115.sp else 130.sp,
                fontFamily = BebasNeue,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                lineHeight = 115.sp,
                letterSpacing = if (rank == 10) (-12).sp else (-2).sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = if (rank == 10) (2).dp else 8.dp, y = 6.dp)
            )

            // LAYER 2 (ON TOP): Poster Image layered on top of the number
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(135.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                AsyncImage(
                    model = anime.image,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
