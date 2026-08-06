package com.animetv.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixRed
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val configuration = LocalConfiguration.current

    // 1:1 Seamless continuity scale: starts at 1.0f (exact match with native splash)
    val scaleAnim = remember { Animatable(1.0f) }
    // Slide offset animation (0f -> -1.0f for slide out left)
    val slideAnim = remember { Animatable(0f) }
    // Alpha animation for smooth exit
    val fadeOutAnim = remember { Animatable(1f) }

    var showColdStartMessage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            delay(3000)
            showColdStartMessage = true
        }

        // Phase 1: Subtle elegant pulse (1.0 -> 1.08 -> 1.0)
        scaleAnim.animateTo(
            targetValue = 1.08f,
            animationSpec = tween(durationMillis = 450, easing = EaseOutCubic)
        )
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic)
        )

        // Hold briefly for visual impact
        delay(300)

        // Phase 2: Slide Out Left + Fade Out
        launch {
            slideAnim.animateTo(
                targetValue = -1.0f,
                animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic)
            )
        }
        launch {
            fadeOutAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450, easing = EaseInCubic)
            )
        }

        delay(520)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = slideAnim.value * size.width
                alpha = fadeOutAnim.value
            }
            .background(NetflixBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.scale(scaleAnim.value),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.animetv.app.R.drawable.logo_splash),
                    contentDescription = "Splash Logo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .width(460.dp)
                        .height(230.dp)
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
                    color = NetflixLightGrey,
                    fontSize = 14.sp,
                    fontFamily = Inter
                )
            }
        }
    }
}
