package com.animetv.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixRed
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    // Scale animation: 0.5 -> 1.2 -> 1.0
    val scaleAnim = remember { Animatable(0.5f) }
    // Alpha animation: 0 -> 1
    val alphaAnim = remember { Animatable(0f) }
    // Fade out at end
    val fadeOutAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Phase 1: Fade in + Scale up the N logo
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
        )

        scaleAnim.animateTo(
            targetValue = 1.15f,
            animationSpec = tween(durationMillis = 700, easing = EaseOutCubic)
        )

        // Phase 2: Settle to normal scale
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
        )

        // Hold for a moment
        delay(600)

        // Phase 3: Fade out everything
        fadeOutAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400, easing = EaseInCubic)
        )

        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .graphicsLayer { alpha = fadeOutAnim.value },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .scale(scaleAnim.value)
                .graphicsLayer { alpha = alphaAnim.value }
        ) {
            // Big Red "N" Logo
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetflixRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontFamily = BebasNeue,
                    fontWeight = FontWeight.Normal
                )
            }

            Row {
                Text(
                    text = "ANIME",
                    color = NetflixRed,
                    fontSize = 32.sp,
                    fontFamily = BebasNeue,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 3.sp
                )
                Text(
                    text = " TV",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = BebasNeue,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}
