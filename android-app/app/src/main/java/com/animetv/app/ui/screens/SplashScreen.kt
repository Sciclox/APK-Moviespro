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
import kotlinx.coroutines.launch

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

    var showColdStartMessage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Delay timer to show cold-start message if server is waking up
        launch {
            delay(3000)
            showColdStartMessage = true
        }

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
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(scaleAnim.value)
                    .graphicsLayer { alpha = alphaAnim.value },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.animetv.app.R.drawable.logo_splash),
                    contentDescription = "Splash Logo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .width(320.dp)
                        .height(160.dp)
                )
            }
            if (showColdStartMessage) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    color = NetflixRed,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Conectando con el servidor de streaming...",
                    color = com.animetv.app.ui.theme.NetflixLightGrey,
                    fontSize = 14.sp,
                    fontFamily = com.animetv.app.ui.theme.Inter
                )
            }
        }
    }
}
