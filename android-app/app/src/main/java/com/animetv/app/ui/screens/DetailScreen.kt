package com.animetv.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeDetail
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.EpisodeItem
import com.animetv.app.data.repository.MyListManager
import com.animetv.app.data.repository.WatchHistoryManager
import com.animetv.app.ui.components.ShimmerBox
import com.animetv.app.ui.components.TvEpisodeCard
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixDarkGrey
import com.animetv.app.ui.theme.NetflixLightGrey
import com.animetv.app.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DetailScreen(
    anime: AnimeItem,
    isScreenActive: Boolean = false,
    onBack: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onAnimeSelect: ((AnimeItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var isInMyList by remember(anime.id) { mutableStateOf(MyListManager.isInList(context, anime.id)) }
    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedBatchIndex by remember { mutableIntStateOf(0) }
    var recommendedList by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var lastPlayedEpNum by remember { mutableIntStateOf(-1) }
    val episodeFocusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Focus Requesters
    val backButtonFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    val isMovie = remember(detail, anime) {
        val rawType = (detail?.type ?: anime.type ?: "TV").uppercase()
        rawType.contains("MOVIE") || rawType.contains("PELICULA") || rawType.contains("PELÍCULA")
    }

    val cleanTitleText = remember(detail, anime) {
        val raw = detail?.title ?: anime.title
        val cleanSlugStr = anime.slug.replace(Regex("^(slug|anime|id)-", RegexOption.IGNORE_CASE), "")
        if (raw.contains("perfil", ignoreCase = true) || raw.contains("editar", ignoreCase = true) || raw.startsWith("slug ", ignoreCase = true)) {
            cleanSlugStr.replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        } else {
            raw
        }
    }

    val cleanSynopsisText = remember(detail, anime, cleanTitleText) {
        val raw = detail?.synopsis ?: anime.synopsis ?: ""
        if (raw.contains("editar perfil", ignoreCase = true)) {
            "$cleanTitleText es una aclamada serie de animación japonesa con increíbles batallas e historia envolvente."
        } else if (raw.isNotBlank()) {
            raw
        } else {
            "$cleanTitleText es una aclamada serie de animación japonesa con increíbles batallas e historia envolvente."
        }
    }

    val fallbackEpisodes = remember(anime, isMovie) {
        if (isMovie) {
            listOf(
                EpisodeItem(
                    id = "${anime.id}-movie",
                    number = 1,
                    title = "Película Completa",
                    image = anime.backdrop ?: anime.image,
                    duration = "1 h 45 min",
                    overview = "Disfruta de la película completa de ${anime.title} en calidad Full HD.",
                    slug = "${anime.slug}/1"
                )
            )
        } else {
            List(12) { i ->
                val epNum = i + 1
                EpisodeItem(
                    id = "${anime.id}-ep-$epNum",
                    number = epNum,
                    title = "Episodio $epNum",
                    image = anime.backdrop ?: anime.image,
                    duration = "24 min",
                    overview = "Capítulo $epNum de la serie animada ${anime.title}.",
                    slug = "${anime.slug}/$epNum"
                )
            }
        }
    }

    // Watch history for smart "Continuar" button & episode progress indicators
    val watchHistoryList = remember(isScreenActive, anime.id) { WatchHistoryManager.getHistory(context) }
    val currentAnimeHistory = remember(watchHistoryList, anime.id, anime.slug) {
        watchHistoryList.find { it.animeSlug == anime.slug || it.animeId == anime.id }
    }

    LaunchedEffect(anime.slug) {
        scope.launch {
            try {
                val res = ApiClient.apiService.getAnimeDetails(anime.slug)
                if (res.success) {
                    detail = res.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    // Load recommended/similar anime based on genre
    LaunchedEffect(anime.slug, detail?.genres) {
        val firstGenre = detail?.genres?.firstOrNull()
        try {
            val res = ApiClient.apiService.getCatalog(
                genre = firstGenre?.lowercase()?.replace(" ", "-"),
                page = 1
            )
            if (res.success && res.data.isNotEmpty()) {
                recommendedList = res.data.filter { it.id != anime.id && it.slug != anime.slug }.take(10)
            }
        } catch (_: Exception) {}
    }

    // Silent background pre-fetch for resume/next episode streams so Play button is instant
    LaunchedEffect(detail?.slug) {
        if (detail != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val targetEp = currentAnimeHistory?.episodeNumber ?: 1
                    ApiClient.apiService.getStreamSources(anime.slug, targetEp.toString())
                } catch (_: Exception) {}
            }
        }
    }

    // Group episodes into batches of 30 for long series
    val batchSize = 30
    val allEpisodes = if (detail?.episodes.isNullOrEmpty()) fallbackEpisodes else detail!!.episodes
    val episodeBatches = remember(allEpisodes) {
        if (allEpisodes.size > batchSize) {
            allEpisodes.chunked(batchSize)
        } else {
            emptyList()
        }
    }

    // Auto focus restoration
    LaunchedEffect(isScreenActive, isLoading) {
        if (isScreenActive && !isLoading) {
            delay(250)
            try {
                val targetEpNum = if (lastPlayedEpNum != -1) lastPlayedEpNum else (currentAnimeHistory?.episodeNumber ?: -1)
                
                if (targetEpNum != -1 && episodeFocusRequesters.containsKey(targetEpNum)) {
                    val batchIdx = (targetEpNum - 1) / batchSize
                    if (episodeBatches.isNotEmpty() && batchIdx in episodeBatches.indices && batchIdx != selectedBatchIndex) {
                        selectedBatchIndex = batchIdx
                        delay(100)
                    }
                    listState.animateScrollToItem(5)
                    delay(100)
                    episodeFocusRequesters[targetEpNum]?.requestFocus()
                } else {
                    playButtonFocusRequester.requestFocus()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    val resumeEpisodeNum = currentAnimeHistory?.episodeNumber ?: 1
    val resumeEpisode = remember(allEpisodes, resumeEpisodeNum) {
        allEpisodes.find { it.number == resumeEpisodeNum }
            ?: allEpisodes.firstOrNull()
            ?: fallbackEpisodes.first()
    }

    // Silent pre-fetch of stream sources for target resume episode in background
    LaunchedEffect(resumeEpisode.slug) {
        if (resumeEpisode.slug.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val parts = resumeEpisode.slug.split("/")
                    val animeSlug = parts.firstOrNull() ?: resumeEpisode.slug
                    val epNum = if (parts.size > 1) parts[1] else resumeEpisode.number.toString()
                    com.animetv.app.data.api.ApiClient.apiService.getStreamSources(animeSlug, epNum)
                } catch (_: Exception) {
                    // Silent background pre-fetch fail-safe
                }
            }
        }
    }

    val blurRadius by animateDpAsState(
        targetValue = if (isLoading) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 600),
        label = "blur"
    )

    val loadingOverlayAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.65f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "loadingOverlayAlpha"
    )

    val backdropUrl = remember(detail?.backdrop, detail?.episodes, anime.backdrop, anime.image) {
        detail?.backdrop.takeIf { !it.isNullOrBlank() }
            ?: detail?.episodes?.firstOrNull { !it.image.isNullOrBlank() }?.image
            ?: anime.backdrop.takeIf { !it.isNullOrBlank() }
            ?: anime.image
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        // Hero Backdrop Background
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(true)
                .build(),
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        )

        // Left-to-Right gradient (darker on left for text readability, clear on right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixBlack.copy(alpha = 0.95f),
                            NetflixBlack.copy(alpha = 0.75f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1000f
                    )
                )
        )

        // Top-to-Bottom gradient (blends smoothly into dark list at bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NetflixBlack.copy(alpha = 0.6f),
                            NetflixBlack
                        ),
                        startY = 200f,
                        endY = 1400f
                    )
                )
        )

        // Scrim Overlay when loading
        if (loadingOverlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = loadingOverlayAlpha))
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 72.dp, top = 36.dp, end = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Item 1: ATRÁS Navigation Button (Row 1)
            item {
                Button(
                    onClick = {
                        scope.launch { listState.scrollToItem(0) }
                        onBack()
                    },
                    modifier = Modifier
                        .focusRequester(backButtonFocusRequester)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            scope.launch {
                                listState.scrollToItem(0, 0)
                            }
                        }
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
                            shape = RoundedCornerShape(4.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "←  ATRÁS",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (isLoading) {
                // Item 2: Title, Badges, Genres, and Synopsis Skeleton
                item {
                    Column(
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .width(550.dp)
                    ) {
                        ShimmerBox(width = 340.dp, height = 42.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShimmerBox(width = 80.dp, height = 22.dp)
                            ShimmerBox(width = 50.dp, height = 22.dp)
                            ShimmerBox(width = 90.dp, height = 22.dp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBox(width = 75.dp, height = 24.dp)
                            ShimmerBox(width = 85.dp, height = 24.dp)
                            ShimmerBox(width = 65.dp, height = 24.dp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ShimmerBox(width = 520.dp, height = 75.dp)
                    }
                }

                // Item 3: Action Buttons Skeleton
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ShimmerBox(width = 185.dp, height = 44.dp)
                        ShimmerBox(width = 135.dp, height = 44.dp)
                    }
                }

                // Item 4: Episodes Section Skeleton
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    ShimmerBox(width = 120.dp, height = 26.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(5) {
                            ShimmerBox(width = 220.dp, height = 135.dp)
                        }
                    }
                }

                // Item 5: Similar Anime Section Skeleton
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    ShimmerBox(width = 160.dp, height = 26.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(6) {
                            ShimmerBox(width = 170.dp, height = 240.dp)
                        }
                    }
                }
            } else {
                // Item 2: Title + Metadata
                item {
                    Column {
                        // Limotion Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.animetv.app.R.drawable.limotion_logo),
                                contentDescription = "Limotion Badge",
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.height(20.dp)
                            )
                            Text(
                                text = if (isMovie) "PELÍCULA" else "SERIE",
                                color = NetflixLightGrey,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontFamily = Inter
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Title
                        Text(
                            text = cleanTitleText,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = BebasNeue,
                            lineHeight = 38.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentDetail = detail
                            val score = currentDetail?.score
                            val scoreText = if (score != null && score > 0) {
                                "★ ${String.format("%.1f", score)}"
                            } else {
                                "98% Coincidencia"
                            }
                            Text(scoreText, color = Color(0xFF46D369), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                            Text("${detail?.year ?: 2024}", color = NetflixLightGrey, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)

                            val statusText = detail?.status ?: "FINALIZADO"
                            val isReleasing = statusText.contains("EMISIÓN")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isReleasing) Color(0xFF2ECC71).copy(alpha = 0.2f) else NetflixDarkGrey)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isMovie) "PELÍCULA HD" else (if (isReleasing) "EN EMISIÓN" else "FINALIZADO"),
                                    color = if (isReleasing && !isMovie) Color(0xFF2ECC71) else NetflixLightGrey,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Inter
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(NetflixRed)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("FULL HD", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = Inter)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Genre chips
                        val genres = detail?.genres ?: listOf("Acción", "Aventura", "Fantasía")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            genres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2B2B2B))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(genre, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)
                                }
                            }
                        }
                    }
                }

                // Item 3: Synopsis
                item {
                    Text(
                        text = cleanSynopsisText,
                        color = NetflixLightGrey,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = 4,
                        fontFamily = Inter
                    )
                }

                // Item 4: Action Buttons Row
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val playButtonText = when {
                            isMovie -> "▶  REPRODUCIR PELÍCULA"
                            currentAnimeHistory != null -> "▶  CONTINUAR EP. $resumeEpisodeNum"
                            else -> "▶  REPRODUCIR EP. 1"
                        }

                        // REPRODUCIR / CONTINUAR Button
                        Button(
                            onClick = {
                                com.animetv.app.ui.screens.PlaybackTelemetryTracker.recordT0()
                                onPlayEpisode(resumeEpisode)
                            },
                            modifier = Modifier
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties {
                                    left = FocusRequester.Cancel
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch { listState.animateScrollToItem(1) }
                                    }
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
                                    shape = RoundedCornerShape(4.dp)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = playButtonText,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                fontFamily = Inter
                            )
                        }

                        // + MI LISTA / ✓ EN MI LISTA Button
                        Button(
                            onClick = {
                                isInMyList = MyListManager.toggleAnime(context, anime)
                            },
                            modifier = Modifier
                                .focusProperties {
                                    right = FocusRequester.Cancel
                                },
                            scale = ButtonDefaults.scale(focusedScale = 1.0f),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isInMyList) Color(0xFF2ECC71).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f),
                                contentColor = Color.White,
                                focusedContainerColor = if (isInMyList) Color(0xFF2ECC71) else Color.White,
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
                            Text(
                                text = if (isInMyList) "✓  EN MI LISTA" else "+  MI LISTA",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = Inter
                            )
                        }
                    }
                }

                // Item 5: Episodios / Película Header
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isMovie) "Película" else "Episodios",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BebasNeue
                    )
                }

                // Item 5b: Episode Batch Selector
                if (episodeBatches.isNotEmpty() && !isMovie) {
                    item {
                        TvLazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items(count = episodeBatches.size, key = { idx -> episodeBatches[idx].first().id }) { idx ->
                                val batch = episodeBatches[idx]
                                val startEp = batch.first().number
                                val endEp = batch.last().number
                                val isSelected = selectedBatchIndex == idx
                                val isFirst = idx == 0
                                val isLast = idx == episodeBatches.lastIndex

                                Button(
                                    onClick = { selectedBatchIndex = idx },
                                    modifier = Modifier
                                        .height(32.dp)
                                        .focusProperties {
                                            if (isFirst) left = FocusRequester.Cancel
                                            if (isLast) right = FocusRequester.Cancel
                                        },
                                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isSelected) NetflixRed else Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = "EP $startEp - $endEp",
                                        fontSize = 12.sp,
                                        fontFamily = Inter,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Item 6: Horizontal Episode List
                item {
                    val displayEpisodes = if (isMovie) {
                        allEpisodes.take(1)
                    } else if (episodeBatches.isNotEmpty()) {
                        episodeBatches.getOrNull(selectedBatchIndex) ?: allEpisodes
                    } else {
                        allEpisodes
                    }

                    TvLazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp, end = 24.dp)
                    ) {
                        items(count = displayEpisodes.size, key = { idx -> displayEpisodes[idx].id }) { idx ->
                            val ep = displayEpisodes[idx]
                            val epHistory = watchHistoryList.find {
                                (it.animeSlug == anime.slug || it.animeId == anime.id) && it.episodeNumber == ep.number
                            }
                            val progressFraction = if (epHistory != null && epHistory.durationMs > 0) {
                                (epHistory.lastPositionMs.toFloat() / epHistory.durationMs.toFloat())
                            } else null
                            val isFirst = idx == 0
                            val isLast = idx == displayEpisodes.lastIndex

                            val epRequester = episodeFocusRequesters.getOrPut(ep.number) { FocusRequester() }

                            TvEpisodeCard(
                                episode = ep,
                                onClick = {
                                    lastPlayedEpNum = ep.number
                                    onPlayEpisode(ep)
                                },
                                progressFraction = progressFraction,
                                modifier = Modifier
                                    .focusRequester(epRequester)
                                    .focusProperties {
                                        if (isFirst) left = FocusRequester.Cancel
                                        if (isLast) right = FocusRequester.Cancel
                                    }
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            lastPlayedEpNum = ep.number
                                        }
                                    }
                            )
                        }
                    }
                }

                // Item 7: Temporadas y Relacionados Section
                val relatedItems = detail?.relatedAnime
                val hasRelated = !relatedItems.isNullOrEmpty()
                val relatedDisplayList = if (hasRelated) {
                    relatedItems!!.map { rel ->
                        com.animetv.app.data.model.AnimeItem(
                            id = rel.id,
                            title = rel.title,
                            slug = rel.slug,
                            image = rel.image ?: "https://cdn.jkdesa.com/assets/images/animes/image/${rel.slug}.jpg",
                            type = rel.type,
                            status = rel.relation
                        )
                    }
                } else {
                    emptyList()
                }

                if (relatedDisplayList.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Temporadas y Relacionados",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BebasNeue
                        )
                    }

                    item {
                        TvLazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, end = 24.dp)
                        ) {
                            items(count = relatedDisplayList.size, key = { idx -> "rel-${relatedDisplayList[idx].id}" }) { idx ->
                                val recAnime = relatedDisplayList[idx]
                                val isFirst = idx == 0
                                val isLast = idx == relatedDisplayList.lastIndex

                                com.animetv.app.ui.components.TvAnimeCard(
                                    anime = recAnime,
                                    onClick = { onAnimeSelect?.invoke(recAnime) },
                                    modifier = Modifier
                                        .width(170.dp)
                                        .focusProperties {
                                            if (isFirst) left = FocusRequester.Cancel
                                            if (isLast) right = FocusRequester.Cancel
                                        }
                                )
                            }
                        }
                    }
                }

                // Item 8: Te Recomendamos Section
                val recommendedItems = detail?.recommendedAnime
                val hasRecommended = !recommendedItems.isNullOrEmpty()
                val recommendedDisplayList = if (hasRecommended) {
                    recommendedItems!!.map { rec ->
                        com.animetv.app.data.model.AnimeItem(
                            id = rec.id,
                            title = rec.title,
                            slug = rec.slug,
                            image = rec.image ?: "https://cdn.jkdesa.com/assets/images/animes/image/${rec.slug}.jpg",
                            type = rec.type
                        )
                    }
                } else {
                    recommendedList
                }

                if (recommendedDisplayList.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Te Recomendamos",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BebasNeue
                        )
                    }

                    item {
                        TvLazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, end = 24.dp)
                        ) {
                            items(count = recommendedDisplayList.size, key = { idx -> "rec-${recommendedDisplayList[idx].id}" }) { idx ->
                                val recAnime = recommendedDisplayList[idx]
                                val isFirst = idx == 0
                                val isLast = idx == recommendedDisplayList.lastIndex

                                com.animetv.app.ui.components.TvAnimeCard(
                                    anime = recAnime,
                                    onClick = { onAnimeSelect?.invoke(recAnime) },
                                    modifier = Modifier
                                        .width(170.dp)
                                        .focusProperties {
                                            if (isFirst) left = FocusRequester.Cancel
                                            if (isLast) right = FocusRequester.Cancel
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
