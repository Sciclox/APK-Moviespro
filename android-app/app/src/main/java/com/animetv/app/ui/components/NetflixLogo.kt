package com.animetv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixRed

@Composable
fun NetflixLogo(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Red "N" Badge Icon
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NetflixRed),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "N",
                color = Color.White,
                fontSize = 26.sp,
                fontFamily = BebasNeue,
                fontWeight = FontWeight.Normal
            )
        }

        Row {
            Text(
                text = "ANIME",
                color = NetflixRed,
                fontSize = 24.sp,
                fontFamily = BebasNeue,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            )
            Text(
                text = " TV",
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = BebasNeue,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            )
        }
    }
}
