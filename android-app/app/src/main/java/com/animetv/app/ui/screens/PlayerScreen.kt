package com.animetv.app.ui.screens

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.EpisodeItem
import com.animetv.app.data.model.StreamSource
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
    var isBuffering by remember { mutableStateOf(false) }

    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var isSeekBarFocused by remember { mutableStateOf(false) }
    var scrubTimeMs by remember { mutableLongStateOf(0L) }

    var showNextEpCountdown by remember { mutableStateOf(false) }
    var nextEpCountdownDismissed by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(10) }
    var quickSeekIcon by remember { mutableStateOf<String?>(null) }
    var preloadedNextStreamUrl by remember { mutableStateOf<String?>(null) }
    var hasNextEpisodeAvailable by remember { mutableStateOf(true) }

    // Multi-server state & auto-fallback
    var serverList by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var currentServerIndex by remember { mutableIntStateOf(0) }
    var showServerMenu by remember { mutableStateOf(false) }
    var autoFallbackToast by remember { mutableStateOf<String?>(null) }
    var lastSelectedServerName by remember { mutableStateOf<String?>(null) }

    // Double tap detection for 30s quick skip
    var lastLeftPressTime by remember { mutableLongStateOf(0L) }
    var lastRightPressTime by remember { mutableLongStateOf(0L) }

    // Skip intro state
    var skipIntroDismissed by remember { mutableStateOf(false) }

    val isMovie = remember(anime, episode) {
        val rawType = (anime?.type ?: "").uppercase()
        val isMovieType = rawType.contains("MOVIE") || rawType.contains("PELICULA") || rawType.contains("PELÍCULA")
        val isMovieTitle = episode.title.contains("Película", ignoreCase = true) || episode.title.contains("Pelicula", ignoreCase = true) || episode.id.endsWith("-movie")
        isMovieType || isMovieTitle
    }

    // Persistent error overlay
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live scrub preview state
    var wasPlayingBeforeScrub by remember { mutableStateOf(true) }

    val backButtonFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val firstServerFocusRequester = remember { FocusRequester() }
    val nextEpFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var lastUserInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Keep scrubTimeMs synced when not seeking
    LaunchedEffect(currentTimeMs) {
        if (!isSeekBarFocused) {
            scrubTimeMs = currentTimeMs
        }
    }

    // High-performance shared OkHttpClient singleton with persistent connection pool
    val okHttpClient = remember { com.animetv.app.data.api.ApiClient.okHttpClient }

    // DataSource factory backed by shared OkHttp singleton
    val httpDataSourceFactory = remember(okHttpClient) {
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
    }

    // LoadControl optimized for ultra-fast playback start (750ms initial buffer) and smooth streaming
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // Min buffer: 15 seconds
                50_000, // Max buffer: 50 seconds
                750,    // Buffer for playback start: 750 ms (Ultra Fast!)
                1_500   // Buffer after rebuffer: 1.5 seconds
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // Switch server helper
    var playServer by remember { mutableStateOf<(Int) -> Unit>({}) }

    var currentServerRetryCount by remember { mutableIntStateOf(0) }

    // Create ExoPlayer instance with custom LoadControl, DataSource, and dropped frame detector
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isLoading = false
                        isBuffering = false
                        currentServerRetryCount = 0
                        PlaybackTelemetryTracker.recordT11()
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            isLoading = false
                        }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            PlaybackTelemetryTracker.recordT10()
                            isLoading = false
                        } else if (state == Player.STATE_ENDED) {
                            isLoading = false
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        error.printStackTrace()
                        isLoading = false
                        val errorMsg = error.localizedMessage ?: "Error de red"
                        if (currentServerRetryCount < 2) {
                            currentServerRetryCount++
                            autoFallbackToast = "Reintentando conexión (${currentServerRetryCount}/2)..."
                            scope.launch {
                                delay(1500)
                                playServer(currentServerIndex)
                                delay(3000)
                                autoFallbackToast = null
                            }
                        } else if (serverList.size > 1 && currentServerIndex < serverList.size - 1) {
                            currentServerRetryCount = 0
                            val nextIndex = currentServerIndex + 1
                            val nextServerName = serverList[nextIndex].server
                            autoFallbackToast = "Servidor no disponible. Cambiando a $nextServerName..."
                            playServer(nextIndex)
                            scope.launch {
                                delay(4000)
                                autoFallbackToast = null
                            }
                        } else {
                            errorMessage = "Error al cargar video: $errorMsg"
                        }
                    }
                })
            }
    }

    playServer = remember(serverList, exoPlayer) {
        { index ->
            if (index in serverList.indices) {
                currentServerIndex = index
                val targetServer = serverList[index]
                lastSelectedServerName = targetServer.server
                val url = targetServer.url
                streamUrl = url
                isLoading = true
                errorMessage = null
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                
                val isHls = url.contains(".m3u8") || targetServer.format == "hls"
                val mediaItem = if (isHls) {
                    MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                } else {
                    MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .build()
                }

                val mediaSource = if (isHls) {
                    HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
                } else {
                    DefaultMediaSourceFactory(httpDataSourceFactory).createMediaSource(mediaItem)
                }

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                if (currentPos > 1000) {
                    exoPlayer.seekTo(currentPos)
                }
                exoPlayer.play()
                scope.launch {
                    delay(1500)
                    isLoading = false
                }
            }
        }
    }

    // Save watch history on progress updates and on leaving the player
    DisposableEffect(context, episode, anime) {
        onDispose {
            try {
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val totalDur = exoPlayer.duration.coerceAtLeast(1L)
                if (currentPos > 3_000L || totalDur > 10_000L) {
                    val aId = anime?.id ?: episode.slug.split("/").firstOrNull() ?: episode.slug
                    val aSlug = anime?.slug ?: episode.slug.split("/").firstOrNull() ?: episode.slug
                    val aTitle = anime?.title ?: "Anime TV"
                    val aImg = anime?.image ?: episode.image ?: ""
                    com.animetv.app.data.repository.WatchHistoryManager.saveWatchProgress(
                        context = context,
                        animeId = aId,
                        animeSlug = aSlug,
                        animeTitle = aTitle,
                        animeImage = aImg,
                        backdrop = anime?.backdrop,
                        episodeNumber = episode.number,
                        episodeTitle = episode.title,
                        lastPositionMs = currentPos,
                        durationMs = totalDur
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(currentTimeMs) {
        if (currentTimeMs > 10_000L && currentTimeMs % 10_000L < 1000L) {
            val aId = anime?.id ?: episode.slug.split("/").firstOrNull() ?: episode.slug
            val aSlug = anime?.slug ?: episode.slug.split("/").firstOrNull() ?: episode.slug
            val aTitle = anime?.title ?: "Anime TV"
            val aImg = anime?.image ?: episode.image ?: ""
            com.animetv.app.data.repository.WatchHistoryManager.saveWatchProgress(
                context = context,
                animeId = aId,
                animeSlug = aSlug,
                animeTitle = aTitle,
                animeImage = aImg,
                backdrop = anime?.backdrop,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
                lastPositionMs = currentTimeMs,
                durationMs = durationMs
            )
        }
    }



    // Initial focus placement on Play/Pause button when controls appear
    LaunchedEffect(showControls) {
        if (showControls && !showServerMenu) {
            delay(100)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (!showControls && !showServerMenu) {
            delay(50)
            try {
                rootFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Explicit D-Pad Focus Request into Server Selector Modal
    LaunchedEffect(showServerMenu) {
        if (showServerMenu) {
            delay(150)
            try {
                firstServerFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Explicit D-Pad Focus Request into Next Episode Countdown Modal
    LaunchedEffect(showNextEpCountdown) {
        if (showNextEpCountdown) {
            showControls = false
            delay(150)
            try {
                nextEpFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Explicit D-Pad Focus Request into Error Overlay Modal
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(150)
            try {
                retryFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-hide HUD after 5 seconds of inactivity (disabled while seeking or in server menu)
    LaunchedEffect(showControls, isSeekBarFocused, showServerMenu, lastUserInteractionTime) {
        if (showControls && !isSeekBarFocused && !showServerMenu) {
            delay(5000)
            if (!isSeekBarFocused && !showServerMenu && (System.currentTimeMillis() - lastUserInteractionTime >= 4800)) {
                showControls = false
            }
        }
    }

    // Periodically update progress bar
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTimeMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Active 10-second countdown timer for next episode auto-play
    LaunchedEffect(showNextEpCountdown) {
        if (showNextEpCountdown && onNextEpisode != null && hasNextEpisodeAvailable) {
            countdownSeconds = 10
            while (countdownSeconds > 0 && showNextEpCountdown) {
                delay(1000)
                if (showNextEpCountdown) {
                    countdownSeconds -= 1
                }
            }
            if (showNextEpCountdown && countdownSeconds <= 0) {
                showNextEpCountdown = false
                onNextEpisode.invoke()
            }
        }
    }

    LaunchedEffect(currentTimeMs, durationMs) {
        if (onNextEpisode != null && hasNextEpisodeAvailable && !nextEpCountdownDismissed && durationMs > 30_000 && (durationMs - currentTimeMs) in 1..15_000) {
            if (!showNextEpCountdown && countdownSeconds > 0) {
                showNextEpCountdown = true
            }
        }
    }

    // Silent Background Pre-fetching for Next Episode when reaching 75% progress
    LaunchedEffect(currentTimeMs, durationMs) {
        if (onNextEpisode != null && hasNextEpisodeAvailable && durationMs > 60_000 && currentTimeMs >= (durationMs * 0.75f) && preloadedNextStreamUrl == null) {
            scope.launch {
                try {
                    var animeSlug = anime?.slug?.ifBlank { null }
                        ?: episode.slug.split("/").firstOrNull()
                        ?: ""
                    animeSlug = animeSlug.replace(Regex("-(episode|episodio)-\\d+$", RegexOption.IGNORE_CASE), "").replace(Regex("/$"), "")
                    val nextEpNum = (episode.number + 1).toString()
                    val res = ApiClient.apiService.getStreamSources(animeSlug, nextEpNum)
                    if (res.success && res.data.servers.isNotEmpty()) {
                        preloadedNextStreamUrl = res.data.servers.first().url
                    }
                } catch (e: Exception) {
                    // silent ignore
                }
            }
        }
    }

    // Fetch streaming video source from API
    LaunchedEffect(episode.slug) {
        isLoading = true
        skipIntroDismissed = false
        nextEpCountdownDismissed = false
        errorMessage = null
        preloadedNextStreamUrl = null
        scope.launch {
            try {
                var animeSlug = anime?.slug?.ifBlank { null }
                    ?: episode.slug.split("/").firstOrNull()
                    ?: ""
                animeSlug = animeSlug.replace(Regex("-(episode|episodio)-\\d+$", RegexOption.IGNORE_CASE), "").replace(Regex("/$"), "")
                val epNum = episode.number.toString()

                // Check total episodes count via anime details API
                try {
                    val detailsRes = ApiClient.apiService.getAnimeDetails(animeSlug)
                    if (detailsRes.success && detailsRes.data.episodes.isNotEmpty()) {
                        val maxEp = detailsRes.data.episodes.maxOfOrNull { it.number } ?: detailsRes.data.episodes.size
                        hasNextEpisodeAvailable = episode.number < maxEp
                    }
                } catch (e: Exception) {
                    // Keep default fallback if details fetch fails
                }

                PlaybackTelemetryTracker.recordT2()
                val res = ApiClient.apiService.getStreamSources(animeSlug, epNum)
                PlaybackTelemetryTracker.recordT3()
                if (res.success && res.data.servers.isNotEmpty()) {
                    serverList = res.data.servers
                    // Check if the user previously selected a preferred server
                    val savedIdx = if (!lastSelectedServerName.isNullOrEmpty()) {
                        res.data.servers.indexOfFirst { s ->
                            s.server.equals(lastSelectedServerName, ignoreCase = true)
                        }
                    } else -1

                    val preferredIdx = if (savedIdx != -1) {
                        savedIdx
                    } else {
                        // Smart pre-selection fallback: Prefer HLS (most stable on TV) > Direct MP4 > Proxy
                        res.data.servers.indexOfFirst { s ->
                            s.format == "hls" || s.url.contains(".m3u8")
                        }.takeIf { it != -1 } ?: 0
                    }

                    currentServerIndex = preferredIdx
                    streamUrl = res.data.servers[preferredIdx].url
                    lastSelectedServerName = res.data.servers[preferredIdx].server
                } else {
                    errorMessage = "El Episodio ${episode.number} no está disponible."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error al obtener las fuentes del Episodio ${episode.number}."
            } finally {
                if (!streamUrl.isNullOrEmpty() && errorMessage == null) {
                    streamUrl?.let { url ->
                        val isHls = url.contains(".m3u8") || serverList.getOrNull(currentServerIndex)?.format == "hls"
                        val mediaItem = if (isHls) {
                            MediaItem.Builder()
                                .setUri(Uri.parse(url))
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                        } else {
                            MediaItem.Builder()
                                .setUri(Uri.parse(url))
                                .setMimeType(MimeTypes.VIDEO_MP4)
                                .build()
                        }

                        val mediaSource = if (isHls) {
                            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
                        } else {
                            DefaultMediaSourceFactory(httpDataSourceFactory).createMediaSource(mediaItem)
                        }

                        exoPlayer.setMediaSource(mediaSource)
                        PlaybackTelemetryTracker.recordT4()
                        exoPlayer.prepare()
                        exoPlayer.play()
                        
                        val prefs = context.getSharedPreferences("player_positions", Context.MODE_PRIVATE)
                        val historyMatch = com.animetv.app.data.repository.WatchHistoryManager.getHistory(context)
                            .find { (it.animeSlug == anime?.slug || it.animeId == anime?.id) && it.episodeNumber == episode.number }
                        val savedPosition = historyMatch?.lastPositionMs?.takeIf { it > 5_000 }
                            ?: prefs.getLong(episode.slug, 0L)
                        val totalDuration = exoPlayer.duration
                        if (savedPosition > 5_000 && (totalDuration <= 0 || savedPosition < totalDuration - 15_000)) {
                            exoPlayer.seekTo(savedPosition)
                        }
                    }
                }
                isLoading = false
            }
        }
    }

    DisposableEffect(episode.slug) {
        onDispose {
            try {
                val prefs = context.getSharedPreferences("player_positions", Context.MODE_PRIVATE)
                if (exoPlayer.currentPosition > 5_000 && exoPlayer.duration > 0 && (exoPlayer.duration - exoPlayer.currentPosition) > 15_000) {
                    prefs.edit().putLong(episode.slug, exoPlayer.currentPosition).apply()
                } else {
                    prefs.edit().remove(episode.slug).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    exoPlayer.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
                    lastUserInteractionTime = System.currentTimeMillis()
                    if (showServerMenu) {
                        if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            showServerMenu = false
                            return@onKeyEvent true
                        }
                    } else if (showNextEpCountdown) {
                        if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            showNextEpCountdown = false
                            nextEpCountdownDismissed = true
                            showControls = true
                            return@onKeyEvent true
                        }
                    } else if (errorMessage != null) {
                        if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            onBack()
                            return@onKeyEvent true
                        }
                    } else if (!showControls) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val now = System.currentTimeMillis()
                                val seekAmount = if (now - lastLeftPressTime < 400) 30_000L else 10_000L
                                lastLeftPressTime = now
                                exoPlayer.seekTo((exoPlayer.currentPosition - seekAmount).coerceAtLeast(0))
                                quickSeekIcon = if (seekAmount == 30_000L) "↺30" else "↺10"
                                scope.launch { delay(800); quickSeekIcon = null }
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val now = System.currentTimeMillis()
                                val seekAmount = if (now - lastRightPressTime < 400) 30_000L else 10_000L
                                lastRightPressTime = now
                                exoPlayer.seekTo((exoPlayer.currentPosition + seekAmount).coerceAtMost(exoPlayer.duration))
                                quickSeekIcon = if (seekAmount == 30_000L) "30↻" else "10↻"
                                scope.launch { delay(800); quickSeekIcon = null }
                                return@onKeyEvent true
                            }
                            else -> {
                                showControls = true
                                return@onKeyEvent true
                            }
                        }
                    }
                }
                false
            }
    ) {
        // ExoPlayer Media Player View (100% Fullscreen)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = isPlaying || isBuffering || isLoading
            },
            modifier = Modifier.fillMaxSize()
        )

        // Quick Seek Icon Overlay
        AnimatedVisibility(
            visible = quickSeekIcon != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = quickSeekIcon ?: "",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter
            )
        }

        // Buffering Indicator (Centered Spinner)
        if (isBuffering && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = NetflixRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Auto-Fallback Notification Toast Banner
        AnimatedVisibility(
            visible = autoFallbackToast != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NetflixRed,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
            ) {
                Text(
                    text = autoFallbackToast ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // Next Episode Countdown Overlay Card
        AnimatedVisibility(
            visible = showNextEpCountdown && onNextEpisode != null && hasNextEpisodeAvailable,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, NetflixRed)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Siguiente episodio en", color = NetflixLightGrey, fontSize = 13.sp, fontFamily = Inter)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$countdownSeconds segundos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = BebasNeue)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onNextEpisode?.invoke() },
                            modifier = Modifier.focusRequester(nextEpFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixRed,
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = NetflixRed
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("Ver ahora", fontFamily = Inter, fontSize = 13.sp) }
                        Button(
                            onClick = {
                                showNextEpCountdown = false
                                nextEpCountdownDismissed = true
                                showControls = true
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("Cancelar", fontFamily = Inter, fontSize = 13.sp) }
                    }
                }
            }
        }

        // Initial Loading Spinner (Before First Frame)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NetflixRed, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando reproductor...", color = Color.White, fontSize = 16.sp, fontFamily = Inter)
                }
            }
        }

        // Error Overlay with Retry Button
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = NetflixRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                    Text(
                        text = "Servidor: ${serverList.getOrNull(currentServerIndex)?.server ?: "Desconocido"}",
                        color = NetflixLightGrey,
                        fontSize = 13.sp,
                        fontFamily = Inter
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                errorMessage = null
                                playServer(currentServerIndex)
                            },
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixRed,
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = NetflixRed
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Reintentar", fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        if (serverList.size > 1) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    val nextIdx = (currentServerIndex + 1) % serverList.size
                                    playServer(nextIdx)
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Probar otro servidor", fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // HUD Overlay Controls
        AnimatedVisibility(
            visible = showControls && !showServerMenu && !showNextEpCountdown,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Gradient Mask
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .padding(32.dp)
                ) {
                    // Top Bar Header (Layer 1 — ↓ goes to Seekbar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                    try { seekBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                    return@onKeyEvent true
                                }
                                false
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .focusRequester(backButtonFocusRequester)
                                .focusProperties {
                                    down = seekBarFocusRequester
                                },
                            scale = ButtonDefaults.scale(focusedScale = 1.0f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.25f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", modifier = Modifier.size(18.dp))
                            }
                        }

                        val subtitleText = remember(episode, isMovie) {
                            if (isMovie) {
                                if (episode.title.contains("Película", ignoreCase = true) || episode.title.contains("Pelicula", ignoreCase = true)) {
                                    episode.title
                                } else {
                                    "Película Completa"
                                }
                            } else if (episode.title.startsWith("Episodio", ignoreCase = true)) {
                                episode.title
                            } else {
                                "Episodio ${episode.number}: ${episode.title}"
                            }
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
                                text = subtitleText,
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
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Interactive Progress Bar, Time Labels & Floating Preview Tooltip
                        val displayTimeMs = if (isSeekBarFocused) scrubTimeMs else currentTimeMs
                        val progress = if (durationMs > 0) displayTimeMs.toFloat() / durationMs.toFloat() else 0f
                        val currentFormatted = formatTime(displayTimeMs)
                        val totalFormatted = formatTime(durationMs)



                        // Floating Time Badge Tooltip Card (Only displays the time number when seekbar is focused)
                        AnimatedVisibility(
                            visible = isSeekBarFocused,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                val tooltipWidth = maxWidth
                                val badgeOffset = (tooltipWidth - 70.dp) * progress.coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier.offset(x = badgeOffset.coerceAtLeast(0.dp))
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50.dp),
                                        color = Color(0xFF222222).copy(alpha = 0.95f),
                                        border = null
                                    ) {
                                        Text(
                                            text = formatTime(scrubTimeMs),
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = Inter,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Seekbar + Total Duration in the SAME Row with small spacing
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Interactive Red Netflix TV Seek Bar Track & Playhead Dot (●)
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .onFocusChanged { focusState ->
                                        isSeekBarFocused = focusState.isFocused
                                    }
                                    .focusRequester(seekBarFocusRequester)
                                    .focusProperties {
                                        up = backButtonFocusRequester
                                        down = playButtonFocusRequester
                                    }
                                    .focusable()
                                    .padding(vertical = 4.dp)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            lastUserInteractionTime = System.currentTimeMillis()
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    scrubTimeMs = (scrubTimeMs - 10_000L).coerceAtLeast(0L)
                                                    exoPlayer.seekTo(scrubTimeMs) // Live frame preview
                                                    return@onKeyEvent true
                                                }
                                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    scrubTimeMs = (scrubTimeMs + 10_000L).coerceAtMost(durationMs)
                                                    exoPlayer.seekTo(scrubTimeMs) // Live frame preview
                                                    return@onKeyEvent true
                                                }
                                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    // Layer navigation: Seekbar → Back button
                                                    try { backButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                                                    return@onKeyEvent true
                                                }
                                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    // Layer navigation: Seekbar → Control buttons
                                                    try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                                                    return@onKeyEvent true
                                                }
                                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                                android.view.KeyEvent.KEYCODE_ENTER -> {
                                                    exoPlayer.seekTo(scrubTimeMs)
                                                    if (wasPlayingBeforeScrub) exoPlayer.play()
                                                    return@onKeyEvent true
                                                }
                                            }
                                        }
                                        false
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val trackWidth = maxWidth
                                // Animated Track Height (Straight track, no borders: 3.5.dp -> 4.5.dp)
                                val trackHeight by animateDpAsState(targetValue = if (isSeekBarFocused) 4.5.dp else 3.5.dp, label = "trackHeight")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(trackHeight)
                                        .background(Color.White.copy(alpha = 0.3f))
                                ) {
                                    val bufferProgress = if (durationMs > 0) bufferedMs.toFloat() / durationMs.toFloat() else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(bufferProgress.coerceIn(0f, 1f))
                                            .background(Color.White.copy(alpha = 0.5f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                                            .background(NetflixRed)
                                    )
                                }

                                // Pure Red Playhead Dot (●) - Positioned with exact offset across fixed track
                                val dotSize by animateDpAsState(targetValue = if (isSeekBarFocused) 15.dp else 11.dp, label = "dotSize")
                                val dotOffset = (trackWidth - dotSize) * progress.coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .offset(x = dotOffset)
                                        .size(dotSize)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(NetflixRed)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = totalFormatted,
                                color = NetflixLightGrey,
                                fontSize = 13.sp,
                                fontFamily = Inter
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Control Buttons Row (Left-aligned)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                        try { seekBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                        return@onPreviewKeyEvent true
                                    }
                                    false
                                },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous Episode Button
                            if (onPreviousEpisode != null) {
                                Button(
                                    onClick = { onPreviousEpisode() },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .onPreviewKeyEvent { k ->
                                            if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) true else false
                                        },
                                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.25f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color.White),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                    ),
                                    shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Episodio Anterior", modifier = Modifier.size(22.dp))
                                    }
                                }
                            }

                            // -10 Seconds
                            Button(
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                                modifier = Modifier
                                    .size(42.dp)
                                    .then(
                                        if (onPreviousEpisode == null) Modifier.onPreviewKeyEvent { k ->
                                            if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) true else false
                                        } else Modifier
                                    ),
                                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Replay10, contentDescription = "-10 Segundos", modifier = Modifier.size(22.dp))
                                }
                            }

                            // Play / Pause Main Action (Hero Compact Circular Button)
                            Button(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .focusRequester(playButtonFocusRequester),
                                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // +10 Seconds
                            Button(
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                                modifier = Modifier.size(42.dp),
                                scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, Color.White),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Forward10, contentDescription = "+10 Segundos", modifier = Modifier.size(22.dp))
                                }
                            }

                            // Next Episode Button
                            if (!isMovie && onNextEpisode != null && hasNextEpisodeAvailable) {
                                Button(
                                    onClick = { onNextEpisode() },
                                    modifier = Modifier.size(42.dp),
                                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.25f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color.White),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                    ),
                                    shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Siguiente Episodio", modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Server Selection Dialog Overlay Modal (With D-Pad Focus & Scroll Support)
        if (showServerMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(440.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(14.dp),
                    color = NetflixDarkGrey,
                    border = BorderStroke(1.5.dp, NetflixRed.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "SELECCIONAR SERVIDOR DE VIDEO",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BebasNeue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Elige cualquier opción con el control remoto si un servidor va pesado.",
                            color = NetflixLightGrey,
                            fontSize = 12.sp,
                            fontFamily = Inter
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Scrollable Container for all 11+ Servers
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 290.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(serverList) { idx, s ->
                                val isSelected = idx == currentServerIndex
                                Button(
                                    onClick = {
                                        playServer(idx)
                                        showServerMenu = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (idx == 0) Modifier.focusRequester(firstServerFocusRequester) else Modifier),
                                    scale = ButtonDefaults.scale(focusedScale = 1.05f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSelected) NetflixRed else Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color.White),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = s.title,
                                            fontFamily = Inter,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = "● En uso",
                                                fontFamily = Inter,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showServerMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                        ) {
                            Text("Cerrar", fontFamily = Inter, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = (ms / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

object PlaybackTelemetryTracker {
    private var t0: Long = 0L
    private var t1: Long = 0L
    private var t2: Long = 0L
    private var t3: Long = 0L
    private var t4: Long = 0L
    private var t10: Long = 0L
    private var t11: Long = 0L

    fun recordT0() {
        t0 = System.currentTimeMillis()
        android.util.Log.d("PlayerTelemetry", "==================================================")
        android.util.Log.d("PlayerTelemetry", "⏱️ [T0] User clicked PLAY button at $t0 ms")
    }

    fun recordT1() {
        val now = System.currentTimeMillis()
        if (t0 == 0L) {
            t0 = now
            android.util.Log.d("PlayerTelemetry", "==================================================")
            android.util.Log.d("PlayerTelemetry", "⏱️ [T0] Initialized playback tracking at $t0 ms")
        }
        t1 = now
        val elapsed = t1 - t0
        android.util.Log.d("PlayerTelemetry", "⏱️ [T1] PlayerScreen LaunchedEffect started (+$elapsed ms since T0)")
    }

    fun recordT2() {
        t2 = System.currentTimeMillis()
        val elapsed = if (t0 > 0) t2 - t0 else 0
        android.util.Log.d("PlayerTelemetry", "⏱️ [T2] Sent /watch API request (+$elapsed ms since T0)")
    }

    fun recordT3() {
        t3 = System.currentTimeMillis()
        val elapsed = if (t0 > 0) t3 - t0 else 0
        val duration = if (t2 > 0) t3 - t2 else 0
        android.util.Log.d("PlayerTelemetry", "⏱️ [T3] Received /watch API response in $duration ms (+$elapsed ms since T0)")
    }

    fun recordT4() {
        t4 = System.currentTimeMillis()
        val elapsed = if (t0 > 0) t4 - t0 else 0
        android.util.Log.d("PlayerTelemetry", "⏱️ [T4] exoPlayer.prepare() called (+$elapsed ms since T0)")
    }

    fun recordT10() {
        t10 = System.currentTimeMillis()
        val elapsed = if (t0 > 0) t10 - t0 else 0
        android.util.Log.d("PlayerTelemetry", "⏱️ [T10] ExoPlayer state = STATE_READY (+$elapsed ms since T0)")
    }

    fun recordT11() {
        t11 = System.currentTimeMillis()
        val elapsed = if (t0 > 0) t11 - t0 else 0
        android.util.Log.d("PlayerTelemetry", "⏱️ [T11] 🎬 FIRST FRAME RENDERED ON SCREEN (TOTAL STARTUP TIME: $elapsed ms)")
        android.util.Log.d("PlayerTelemetry", "==================================================")
    }
}
