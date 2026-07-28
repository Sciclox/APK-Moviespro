package com.animetv.app.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.EpisodeItem
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixDarkGrey
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    anime: AnimeItem?,
    episode: EpisodeItem,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    val playButtonFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Create ExoPlayer instance with custom LoadControl to prevent audio/video stuttering
    val exoPlayer = remember {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min buffer to keep: 30s (prevents starvation)
                60_000, // Max buffer: 60s
                2_500,  // Buffer to start playback: 2.5s (quick startup)
                5_000   // Buffer to resume after rebuffer: 5s
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            }
    }

    // Auto-hide HUD after 5 seconds of inactivity & transfer focus to root Box when hidden
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(150)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(5000)
            showControls = false
        } else {
            delay(50)
            try {
                rootFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Periodically update progress bar
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTimeMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
            delay(500)
        }
    }

    // Fetch streaming video source from API
    LaunchedEffect(episode.slug) {
        isLoading = true
        scope.launch {
            try {
                val parts = episode.slug.split("/")
                val animeSlug = parts.getOrNull(0) ?: ""
                val epNum = parts.getOrNull(1) ?: episode.number.toString()

                val res = ApiClient.apiService.getStreamSources(animeSlug, epNum)
                if (res.success && res.data.servers.isNotEmpty()) {
                    streamUrl = res.data.servers.first().url
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (streamUrl.isNullOrEmpty()) {
                    // Reliable fallback HLS stream
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                }
                streamUrl?.let { url ->
                    val mediaItem = MediaItem.fromUri(Uri.parse(url))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Root container intercepting TV remote keys to show controls
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (!showControls) {
                        showControls = true
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando reproductor...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
            }
        } else {
            // Fullscreen Native Media3 PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Netflix Custom TV Compose HUD
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Sleek Netflix-Style TV HUD Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(32.dp)
                ) {
                    // Top Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBack,
                            scale = ButtonDefaults.scale(focusedScale = 1.12f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.25f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                        ) {
                            Text("← VOLVER", fontWeight = FontWeight.Bold, fontFamily = Inter)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = anime?.title ?: "ANIME TV",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BebasNeue
                            )
                            Text(
                                text = "Episodio ${episode.number} - ${episode.title}",
                                color = NetflixLightGrey,
                                fontSize = 14.sp,
                                fontFamily = Inter
                            )
                        }
                    }

                    // Bottom Bar Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress Bar & Time Labels
                        val progress = if (durationMs > 0) currentTimeMs.toFloat() / durationMs.toFloat() else 0f
                        val currentFormatted = formatTime(currentTimeMs)
                        val totalFormatted = formatTime(durationMs)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentFormatted, color = Color.White, fontSize = 12.sp, fontFamily = Inter)
                            Text(totalFormatted, color = NetflixLightGrey, fontSize = 12.sp, fontFamily = Inter)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Red Netflix TV Seek Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .background(NetflixRed)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Control Buttons Row with unified TV style
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous Episode Button
                            if (onPreviousEpisode != null) {
                                Button(
                                    onClick = { onPreviousEpisode() },
                                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.25f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color.White),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                                ) {
                                    Text("⏮ EP. ANTERIOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                                }
                            }

                            // -10 Seconds
                            Button(
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                                scale = ButtonDefaults.scale(focusedScale = 1.12f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                            ) {
                                Text("↺ -10s", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                            }

                            // Play / Pause Main Action
                            Button(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier.focusRequester(playButtonFocusRequester),
                                scale = ButtonDefaults.scale(focusedScale = 1.15f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = if (isPlaying) "⏸  PAUSA" else "▶  REPRODUCIR",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Inter
                                )
                            }

                            // +10 Seconds
                            Button(
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                                scale = ButtonDefaults.scale(focusedScale = 1.12f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                            ) {
                                Text("↻ +10s", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                            }

                            // Next Episode Button
                            if (onNextEpisode != null) {
                                Button(
                                    onClick = { onNextEpisode() },
                                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.25f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color.White),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                                ) {
                                    Text("⏭ SIGUIENTE EP.", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
