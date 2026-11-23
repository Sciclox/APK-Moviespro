@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.animetv.app.ui.screens

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
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
import coil.request.ImageRequest
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

@OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.media3.common.util.UnstableApi::class
)
@Composable
fun PlayerScreen(
    anime: AnimeItem?,
    episode: EpisodeItem,
    onBack: () -> Unit,
    onGoToDetail: (() -> Unit)? = null,
    onSelectEpisode: ((EpisodeItem) -> Unit)? = null,
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

    // Episodes side drawer state
    var episodesList by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var showEpisodesMenu by remember { mutableStateOf(false) }
    val episodesListState = rememberLazyListState()
    val episodesFocusRequester = remember { FocusRequester() }

    val displayEpisodes = remember(episodesList, episode, anime) {
        if (episodesList.isNotEmpty()) {
            episodesList
        } else {
            val aSlug = anime?.slug?.ifBlank { null }
                ?: episode.slug.split("/").firstOrNull()
                ?: ""
            val cleanSlug = aSlug.replace(Regex("-(episode|episodio)-\\d+$", RegexOption.IGNORE_CASE), "").replace(Regex("/$"), "")
            val total = (episode.number + 5).coerceAtLeast(12)
            (1..total).map { num ->
                EpisodeItem(
                    id = "$cleanSlug-episode-$num",
                    number = num,
                    title = "Episodio $num",
                    slug = "$cleanSlug/$num"
                )
            }
        }
    }

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
    val episodesButtonFocusRequester = remember { FocusRequester() }
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
    var currentServerRetryCount by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    val currentServerListState = rememberUpdatedState(serverList)
    val currentServerIndexState = rememberUpdatedState(currentServerIndex)
    val currentRetryCountState = rememberUpdatedState(currentServerRetryCount)

    // Create ExoPlayer instance with custom LoadControl and DataSource
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    val playServerAtIndex: (Int) -> Unit = remember(exoPlayer, httpDataSourceFactory) {
        { index ->
            try {
                val list = currentServerListState.value
                if (index in list.indices) {
                    currentServerIndex = index
                    val targetServer = list[index]
                    lastSelectedServerName = targetServer.server
                    val url = targetServer.url
                    streamUrl = url
                    isLoading = true
                    errorMessage = null
                    val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)

                    val isEmbed = targetServer.format == "embed" || url.contains("cdn.tudorama") || url.contains("abyss") || url.contains("filemoon") || url.contains("hgcloud") || url.contains("earnvids") || url.contains("4meplayer")
                    if (isEmbed) {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        scope.launch {
                            delay(500)
                            isLoading = false
                        }
                    } else {
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
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
                errorMessage = "Error al iniciar el servidor seleccionado."
            }
        }
    }

    val playServer = playServerAtIndex

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
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
                try {
                    val errorMsg = error.localizedMessage ?: "Error de red"
                    val list = currentServerListState.value
                    val curIdx = currentServerIndexState.value
                    if (list.size > 1 && curIdx < list.size - 1) {
                        currentServerRetryCount = 0
                        val nextIndex = curIdx + 1
                        val nextServer = list[nextIndex]
                        autoFallbackToast = "Cambiando a ${nextServer.server}..."
                        playServerAtIndex(nextIndex)
                        scope.launch {
                            delay(3000)
                            autoFallbackToast = null
                        }
                    } else if (currentRetryCountState.value < 1) {
                        currentServerRetryCount++
                        autoFallbackToast = "Reintentando conexión..."
                        scope.launch {
                            delay(1200)
                            playServerAtIndex(curIdx)
                            delay(3000)
                            autoFallbackToast = null
                        }
                    } else {
                        errorMessage = "Error al reproducir: $errorMsg"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Error en la reproducción de video."
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
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



    // Initial focus placement on Play/Pause or Episodes button when controls appear
    LaunchedEffect(showControls, showServerMenu, showEpisodesMenu) {
        if (showControls && !showServerMenu && !showEpisodesMenu) {
            delay(120)
            try {
                episodesButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                try {
                    playButtonFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        } else if (!showControls && !showServerMenu && !showEpisodesMenu) {
            delay(50)
            try {
                rootFocusRequester.requestFocus()
            } catch (e: Exception) {}
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

    // Silent Background Pre-fetching for Next Episode
    LaunchedEffect(currentTimeMs, durationMs) {
        if (onNextEpisode != null && hasNextEpisodeAvailable && durationMs > 60_000 && currentTimeMs >= (durationMs * 0.75f) && preloadedNextStreamUrl == null) {
            scope.launch {
                try {
                    val targetUrl = anime?.slug?.ifBlank { null } ?: episode.slug
                    val res = com.animetv.app.data.repository.AnimeRepository.extractStream(targetUrl)
                    if (res is com.animetv.app.data.repository.RepositoryResult.Success && res.value.streams.isNotEmpty()) {
                        preloadedNextStreamUrl = res.value.streams.first().url
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Fetch streaming video source from API
    LaunchedEffect(episode.slug) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        isLoading = true
        skipIntroDismissed = false
        nextEpCountdownDismissed = false
        errorMessage = null
        preloadedNextStreamUrl = null
        try {
            val targetUrl = episode.slug.ifBlank { anime?.slug ?: "" }
            val isDirectStream = targetUrl.contains(".m3u8") || anime?.type == "tv_live" || targetUrl.contains("akamaized.net")

            if (isDirectStream) {
                val directServer = StreamSource(
                    server = "Señal Principal",
                    title = "En Vivo HD",
                    url = targetUrl,
                    format = "hls",
                    audio = "Latino"
                )
                serverList = listOf(directServer)
                currentServerIndex = 0
                streamUrl = targetUrl
                lastSelectedServerName = directServer.server
            } else {
                // If episodesList is empty, fetch episodes for this series using anime slug
                if (episodesList.isEmpty()) {
                    try {
                        val seriesSlug = anime?.slug ?: ""
                        if (seriesSlug.contains("/tvshows/")) {
                            val detailsRes = com.animetv.app.data.repository.AnimeRepository.getMovieDetails(seriesSlug)
                            if (detailsRes is com.animetv.app.data.repository.RepositoryResult.Success && detailsRes.value.episodes.isNotEmpty()) {
                                episodesList = detailsRes.value.episodes
                                val maxEp = detailsRes.value.episodes.maxOfOrNull { it.number } ?: detailsRes.value.episodes.size
                                hasNextEpisodeAvailable = episode.number < maxEp
                            }
                        }
                    } catch (_: Exception) {}
                }

                PlaybackTelemetryTracker.recordT2()
                val res = com.animetv.app.data.repository.AnimeRepository.extractStream(targetUrl)
                PlaybackTelemetryTracker.recordT3()
                if (res is com.animetv.app.data.repository.RepositoryResult.Success && res.value.streams.isNotEmpty()) {
                    val cleaned = res.value.streams.mapIndexed { idx, s ->
                        var rawUrl = s.url
                        if (rawUrl.contains("url=")) {
                            val encodedParam = rawUrl.substringAfter("url=").substringBefore("&")
                            try {
                                val decodedBytes = android.util.Base64.decode(encodedParam, android.util.Base64.DEFAULT)
                                val decodedUrl = String(decodedBytes, Charsets.UTF_8)
                                if (decodedUrl.startsWith("http")) {
                                    rawUrl = decodedUrl
                                }
                            } catch (_: Exception) {}
                        }
                        StreamSource(
                            server = s.source ?: "Servidor ${idx + 1}",
                            title = "HD 720p",
                            url = rawUrl,
                            format = if (rawUrl.contains(".m3u8")) "hls" else s.type,
                            audio = "Latino"
                        )
                    }

                    val sortedServers = cleaned.sortedByDescending {
                        when {
                            it.server.contains("MovieDays", ignoreCase = true) -> 10
                            it.url.contains("moviedays", ignoreCase = true) || it.url.contains("11231data") -> 9
                            it.url.contains(".m3u8") || it.format == "hls" -> 8
                            it.url.contains(".aaa.mp4") || it.url.contains(".caa.mp4") -> 7
                            it.url.contains("1a-1791.com") -> 5
                            else -> 1
                        }
                    }

                    serverList = sortedServers
                    val preferredIdx = 0
                    currentServerIndex = preferredIdx
                    streamUrl = sortedServers[preferredIdx].url
                    lastSelectedServerName = sortedServers[preferredIdx].server
                } else {
                    errorMessage = "El contenido no está disponible actualmente."
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Error al obtener las fuentes de reproducción."
        } finally {
            try {
                if (!streamUrl.isNullOrEmpty() && errorMessage == null) {
                    streamUrl?.let { url ->
                        val serverFormat = serverList.getOrNull(currentServerIndex)?.format
                        val isEmbed = serverFormat == "embed" || url.contains("cdn.tudorama") || url.contains("abyss") || url.contains("filemoon") || url.contains("hgcloud") || url.contains("earnvids") || url.contains("4meplayer")

                        if (isEmbed) {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        } else {
                            val isHls = url.contains(".m3u8") || serverFormat == "hls"
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error al iniciar la reproducción."
            }
            isLoading = false
        }
    }

    // Save watch position on episode change
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
            }
        }
    }

    // Release ExoPlayer instance ONLY when leaving PlayerScreen completely
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
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
                    if (showEpisodesMenu) {
                        if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            showEpisodesMenu = false
                            showControls = true
                            return@onKeyEvent true
                        }
                    } else if (showServerMenu) {
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
                        val currentEmbedUrl = streamUrl
                        val isCurrentEmbed = currentEmbedUrl?.let {
                            it.contains("cdn.tudorama") || it.contains("abyss") || it.contains("filemoon") || it.contains("hgcloud") || it.contains("earnvids") || it.contains("4meplayer") || serverList.getOrNull(currentServerIndex)?.format == "embed"
                        } ?: false

                        if (isCurrentEmbed && (keyEvent.nativeKeyEvent.keyCode in listOf(
                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                android.view.KeyEvent.KEYCODE_ENTER,
                                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                            ))
                        ) {
                            webViewRef?.evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v) {
                                        if (v.paused) v.play(); else v.pause();
                                        return;
                                    }
                                    var playSelectors = ['.vjs-big-play-button', '.jw-display-icon-container', '.play-button', '.play-btn', 'button[aria-label="Play"]', '.plyr__control--overlaid', '#player', '#playback', '.play'];
                                    for (var j = 0; j < playSelectors.length; j++) {
                                        var el = document.querySelector(playSelectors[j]);
                                        if (el) { el.click(); return; }
                                    }
                                    var centerEl = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                                    if (centerEl) centerEl.click();
                                })();
                                """.trimIndent(),
                                null
                            )
                            return@onKeyEvent true
                        }

                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val now = System.currentTimeMillis()
                                val seekAmount = if (now - lastLeftPressTime < 400) 30_000L else 10_000L
                                lastLeftPressTime = now
                                if (isCurrentEmbed) {
                                    webViewRef?.evaluateJavascript(
                                        """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) { v.currentTime = Math.max(0, v.currentTime - 10); }
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                } else {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - seekAmount).coerceAtLeast(0))
                                }
                                quickSeekIcon = if (seekAmount == 30_000L) "↺30" else "↺10"
                                scope.launch { delay(800); quickSeekIcon = null }
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val now = System.currentTimeMillis()
                                val seekAmount = if (now - lastRightPressTime < 400) 30_000L else 10_000L
                                lastRightPressTime = now
                                if (isCurrentEmbed) {
                                    webViewRef?.evaluateJavascript(
                                        """
                                        (function() {
                                            var v = document.querySelector('video');
                                            if (v) { v.currentTime = Math.min(v.duration, v.currentTime + 10); }
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                } else {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + seekAmount).coerceAtMost(exoPlayer.duration))
                                }
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
        val currentEmbedUrl = streamUrl
        val isCurrentEmbed = currentEmbedUrl?.let {
            it.contains("cdn.tudorama") || it.contains("abyss") || it.contains("filemoon") || it.contains("hgcloud") || it.contains("earnvids") || it.contains("4meplayer") || serverList.getOrNull(currentServerIndex)?.format == "embed"
        } ?: false

        if (isCurrentEmbed && currentEmbedUrl != null) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onCreateWindow(
                                view: android.webkit.WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean = false
                        }
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        function unlockAndAutoPlay() {
                                            try {
                                                var overlay = document.getElementById('overlay');
                                                if (overlay) {
                                                    overlay.click();
                                                    overlay.style.display = 'none';
                                                    overlay.remove();
                                                }
                                                var playback = document.getElementById('playback');
                                                if (playback) {
                                                    playback.click();
                                                    playback.style.display = 'none';
                                                    playback.remove();
                                                }

                                                var btn = document.getElementById('loadIframeButton');
                                                if (btn) btn.click();

                                                var notice = document.getElementById('notice-container');
                                                if (notice) notice.style.display = 'none';
                                                var msj = document.getElementById('msjad');
                                                if (msj) msj.style.display = 'none';
                                                var ifrC = document.getElementById('iframeContainer');
                                                if (ifrC) ifrC.style.display = 'block';
                                                var ifr = document.getElementById('myIframe');
                                                if (ifr) ifr.style.display = 'block';

                                                if (typeof window.jwplayer !== 'undefined') {
                                                    try {
                                                        var p = window.jwplayer();
                                                        if (p && typeof p.play === 'function') p.play(true);
                                                    } catch(e) {}
                                                }

                                                var videos = document.getElementsByTagName('video');
                                                for (var i = 0; i < videos.length; i++) {
                                                    videos[i].muted = false;
                                                    videos[i].play().catch(function(){});
                                                }

                                                var playTargets = [
                                                    '.jw-display-icon-container',
                                                    '.vjs-big-play-button',
                                                    '.play-button',
                                                    '.play-btn',
                                                    'button[aria-label="Play"]',
                                                    '.plyr__control--overlaid',
                                                    '#player',
                                                    '#playback',
                                                    '.play'
                                                ];
                                                for (var j = 0; j < playTargets.length; j++) {
                                                    var target = document.querySelector(playTargets[j]);
                                                    if (target) target.click();
                                                }

                                                var center = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                                                if (center && center.tagName !== 'VIDEO') {
                                                    center.click();
                                                }
                                            } catch(e) {}
                                        }
                                        unlockAndAutoPlay();
                                        var count = 0;
                                        var timer = setInterval(function() {
                                            unlockAndAutoPlay();
                                            count++;
                                            if (count > 20) clearInterval(timer);
                                        }, 250);
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                            override fun shouldOverrideUrlLoading(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: ""
                                if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                    val isAllowed = reqUrl.contains("abyss") || reqUrl.contains("tudorama") || reqUrl.contains("filemoon") || reqUrl.contains("hgcloud") || reqUrl.contains("earnvids") || reqUrl.contains("4meplayer")
                                    if (isAllowed) return false
                                }
                                return true
                            }
                        }
                        loadUrl(currentEmbedUrl)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    if (webView.url != currentEmbedUrl) {
                        webView.loadUrl(currentEmbedUrl)
                    }
                    webView.keepScreenOn = true
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
        }

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

        // Auto scroll & focus for Episodes side drawer
        LaunchedEffect(showEpisodesMenu) {
            if (showEpisodesMenu) {
                val targetIdx = displayEpisodes.indexOfFirst { it.number == episode.number }.coerceAtLeast(0)
                try {
                    episodesListState.scrollToItem(targetIdx)
                } catch (_: Exception) {}
                delay(100)
                try {
                    episodesFocusRequester.requestFocus()
                } catch (_: Exception) {}
            } else {
                showControls = true
                lastUserInteractionTime = System.currentTimeMillis()
                delay(120)
                try {
                    episodesButtonFocusRequester.requestFocus()
                } catch (_: Exception) {
                    try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            }
        }

        // HUD Overlay Controls
        AnimatedVisibility(
            visible = showControls && !showServerMenu && !showNextEpCountdown && !showEpisodesMenu,
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
                                    up = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
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
                                onClick = {
                                    if (isCurrentEmbed) {
                                        webViewRef?.evaluateJavascript(
                                            """
                                            (function() {
                                                var v = document.querySelector('video');
                                                if (v) { v.currentTime = Math.max(0, v.currentTime - 10); }
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    } else {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                    }
                                },
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
                                    if (isCurrentEmbed) {
                                        webViewRef?.evaluateJavascript(
                                            """
                                            (function() {
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    if (v.paused) v.play(); else v.pause();
                                                    return;
                                                }
                                                var playSelectors = ['.jw-display-icon-container', '.vjs-big-play-button', '.play-button', '.play-btn', 'button[aria-label="Play"]', '.plyr__control--overlaid', '#player', '#playback', '.play'];
                                                for (var j = 0; j < playSelectors.length; j++) {
                                                    var el = document.querySelector(playSelectors[j]);
                                                    if (el) { el.click(); return; }
                                                }
                                                var centerEl = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                                                if (centerEl) centerEl.click();
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
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
                                        contentDescription = "Reproducir / Pausar",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // +10 Seconds
                            Button(
                                onClick = {
                                    if (isCurrentEmbed) {
                                        webViewRef?.evaluateJavascript(
                                            """
                                            (function() {
                                                var v = document.querySelector('video');
                                                if (v) { v.currentTime = Math.min(v.duration, v.currentTime + 10); }
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    } else {
                                        exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                    }
                                },
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

                            // Episodes List Drawer Button
                            if (!isMovie) {
                                Button(
                                    onClick = {
                                        showEpisodesMenu = true
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .focusRequester(episodesButtonFocusRequester),
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
                                        Icon(Icons.Default.PlaylistPlay, contentDescription = "Lista de Episodios", modifier = Modifier.size(22.dp))
                                    }
                                }
                            }

                            // Go to Detail Screen Button
                            if (onGoToDetail != null) {
                                Button(
                                    onClick = { onGoToDetail() },
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
                                        Icon(Icons.Default.Home, contentDescription = "Ver Detalle", modifier = Modifier.size(20.dp))
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

        // Side Drawer Modal: Vertical Episodes List Overlay (Netflix TV Style)
        AnimatedVisibility(
            visible = showEpisodesMenu,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            val targetIdx = remember(displayEpisodes, episode) {
                displayEpisodes.indexOfFirst { it.number == episode.number }.coerceAtLeast(0)
            }

            val currentAnimeSlug = anime?.slug?.ifBlank { null } ?: episode.slug
            val watchHistoryList = remember(showEpisodesMenu) {
                if (showEpisodesMenu) com.animetv.app.data.repository.WatchHistoryManager.getHistory(context) else emptyList()
            }
            val historyMap = remember(watchHistoryList, currentAnimeSlug) {
                watchHistoryList.filter { it.animeSlug == currentAnimeSlug || it.animeId == currentAnimeSlug }
                    .associateBy { it.episodeNumber }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK || keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT)) {
                            showEpisodesMenu = false
                            showControls = true
                            lastUserInteractionTime = System.currentTimeMillis()
                            try { episodesButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                            return@onKeyEvent true
                        }
                        false
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(390.dp)
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        ),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    color = Color(0xFF141414).copy(alpha = 0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header: Title & Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "EPISODIOS (${displayEpisodes.size})",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = BebasNeue
                                )
                                Text(
                                    text = anime?.title ?: "Seleccionar capítulo",
                                    color = NetflixLightGrey,
                                    fontSize = 12.sp,
                                    fontFamily = Inter,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = {
                                    showEpisodesMenu = false
                                    showControls = true
                                    lastUserInteractionTime = System.currentTimeMillis()
                                    try { episodesButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(32.dp),
                                scale = ButtonDefaults.scale(focusedScale = 1.05f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Vertical List of Episodes with Preview Thumbnails & Fixed DPAD Focus
                        LazyColumn(
                            state = episodesListState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(displayEpisodes) { idx, item ->
                                val isCurrent = (item.number == episode.number)
                                var isFocused by remember { mutableStateOf(false) }
                                val thumbUrl = item.image?.ifBlank { null } ?: anime?.image

                                val epProgressFraction = remember(item.number, episode.number, currentTimeMs, durationMs, historyMap) {
                                    if (isCurrent && durationMs > 0L) {
                                        (currentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        val match = historyMap[item.number]
                                        if (match != null && match.durationMs > 0L) {
                                            (match.lastPositionMs.toFloat() / match.durationMs.toFloat()).coerceIn(0f, 1f)
                                        } else null
                                    }
                                }

                                Button(
                                    onClick = {
                                        showEpisodesMenu = false
                                        showControls = true
                                        lastUserInteractionTime = System.currentTimeMillis()
                                        if (!isCurrent) {
                                            if (onSelectEpisode != null) {
                                                onSelectEpisode(item)
                                            } else {
                                                onNextEpisode?.invoke()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (idx == targetIdx) Modifier.focusRequester(episodesFocusRequester) else Modifier)
                                        .onFocusChanged { isFocused = it.isFocused },
                                    scale = ButtonDefaults.scale(focusedScale = 1.02f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isCurrent) NetflixRed.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                    border = ButtonDefaults.border(
                                        border = Border(
                                            border = BorderStroke(
                                                width = if (isFocused) 2.dp else if (isCurrent) 1.5.dp else 1.dp,
                                                color = if (isFocused) Color.White else if (isCurrent) NetflixRed else Color.White.copy(alpha = 0.08f)
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Episode 16:9 Preview Thumbnail Image + Red Progress Bar
                                        Box(
                                            modifier = Modifier
                                                .width(96.dp)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                        ) {
                                            if (!thumbUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(thumbUrl)
                                                        .crossfade(true)
                                                        .size(200, 112)
                                                        .build(),
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(NetflixDarkGrey),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "EP ${item.number}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NetflixLightGrey,
                                                        fontFamily = Inter
                                                    )
                                                }
                                            }

                                            // Netflix Red Progress Bar below thumbnail
                                            if (epProgressFraction != null && epProgressFraction > 0.02f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.5.dp)
                                                        .align(Alignment.BottomStart)
                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(epProgressFraction)
                                                            .background(NetflixRed)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (item.title.startsWith("Episodio", ignoreCase = true)) item.title else "Episodio ${item.number}",
                                                fontSize = 13.sp,
                                                fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = Inter,
                                                maxLines = 1,
                                                color = if (isFocused) Color.Black else Color.White
                                            )
                                            if (!item.title.startsWith("Episodio", ignoreCase = true) && item.title.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.title,
                                                    fontSize = 11.sp,
                                                    fontFamily = Inter,
                                                    maxLines = 1,
                                                    color = if (isFocused) Color(0xFF333333) else NetflixLightGrey
                                                )
                                            }
                                        }

                                        // Live Equalizer Badge for currently playing episode
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(NetflixRed)
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                val transition = rememberInfiniteTransition(label = "eq")
                                                val bar1Height by transition.animateFloat(
                                                    initialValue = 3f, targetValue = 11f,
                                                    animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                                    label = "b1"
                                                )
                                                val bar2Height by transition.animateFloat(
                                                    initialValue = 11f, targetValue = 4f,
                                                    animationSpec = infiniteRepeatable(tween(290, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                                    label = "b2"
                                                )
                                                val bar3Height by transition.animateFloat(
                                                    initialValue = 5f, targetValue = 12f,
                                                    animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                                    label = "b3"
                                                )

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                                    verticalAlignment = Alignment.Bottom,
                                                    modifier = Modifier.height(12.dp)
                                                ) {
                                                    Box(Modifier.width(2.dp).height(bar1Height.dp).background(Color.White))
                                                    Box(Modifier.width(2.dp).height(bar2Height.dp).background(Color.White))
                                                    Box(Modifier.width(2.dp).height(bar3Height.dp).background(Color.White))
                                                }

                                                Spacer(modifier = Modifier.width(4.dp))

                                                Text(
                                                    text = "VIVO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    fontFamily = Inter
                                                )
                                            }
                                        }
                                    }
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
