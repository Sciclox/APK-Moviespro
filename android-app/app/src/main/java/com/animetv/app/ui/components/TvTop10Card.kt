package com.animetv.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.theme.BebasNeue

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
        scale = CardDefaults.scale(focusedScale = 1.0f),
        border = CardDefaults.border(focusedBorder = Border.None),
        colors = CardDefaults.colors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // LAYER 1: Poster Image on the right
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(140.dp)
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

            // LAYER 2 (ON TOP): Giant Rank Number 1..10 (Outlined stroke only, white on focus)
            Text(
                text = "$rank",
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.5f),
                style = TextStyle(
                    drawStyle = Stroke(
                        width = 6f
                    )
                ),
                fontSize = if (rank == 10) 110.sp else 125.sp,
                fontFamily = BebasNeue,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                lineHeight = 110.sp,
                letterSpacing = if (rank == 10) (-8).sp else (-2).sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 4.dp, y = 6.dp)
            )
        }
    }
}

