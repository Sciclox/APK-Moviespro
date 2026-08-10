package com.animetv.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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

    // Watch history
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

    // Load recommended
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

    val allEpisodes = if (detail?.episodes.isNullOrEmpty()) fallbackEpisodes else detail!!.episodes
    val batchSize = 30
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

    var currentBackdropUrl by remember(backdropUrl) { mutableStateOf(backdropUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        key(currentBackdropUrl) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(currentBackdropUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                onError = {
                    val fallbackImg = detail?.episodes?.firstOrNull { !it.image.isNullOrBlank() }?.image
                    if (!fallbackImg.isNullOrBlank() && currentBackdropUrl != fallbackImg) {
                        currentBackdropUrl = fallbackImg
                    } else if (!anime.image.isNullOrBlank() && currentBackdropUrl != anime.image) {
                        currentBackdropUrl = anime.image
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixBlack.copy(alpha = 0.78f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        0.85f to NetflixBlack.copy(alpha = 0.90f),
                        1.0f to NetflixBlack
                    )
                )
        )

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
            item {
                Button(
                    onClick = {
                        onBack()
                    },
                    modifier = Modifier
                        .focusRequester(backButtonFocusRequester)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
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
                            shape = RoundedCornerShape(6.dp)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Atrás",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            if (isLoading) {
                // Skeleton 1: Title + Badges + Genres
                item {
                    Column(modifier = Modifier.padding(top = 10.dp).width(550.dp)) {
                        ShimmerBox(width = 110.dp, height = 18.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(width = 380.dp, height = 44.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShimmerBox(width = 80.dp, height = 22.dp)
                            ShimmerBox(width = 50.dp, height = 22.dp)
                            ShimmerBox(width = 90.dp, height = 22.dp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBox(width = 70.dp, height = 26.dp)
                            ShimmerBox(width = 85.dp, height = 26.dp)
                            ShimmerBox(width = 65.dp, height = 26.dp)
                        }
                    }
                }

                // Skeleton 2: Synopsis Lines
                item {
                    Column(modifier = Modifier.fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(width = 520.dp, height = 16.dp)
                        ShimmerBox(width = 460.dp, height = 16.dp)
                        ShimmerBox(width = 320.dp, height = 16.dp)
                    }
                }

                // Skeleton 3: Action Buttons (Play + My List)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ShimmerBox(width = 180.dp, height = 44.dp)
                        ShimmerBox(width = 140.dp, height = 44.dp)
                    }
                }

                // Skeleton 4: Episodes Header + Episodes Row
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(width = 130.dp, height = 28.dp)
                }

                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(185.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp, end = 24.dp)
                    ) {
                        items(count = 5) {
                            ShimmerBox(width = 230.dp, height = 150.dp)
                        }
                    }
                }

                // Skeleton 5: Recommended Header + Recommended Row
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    ShimmerBox(width = 180.dp, height = 28.dp)
                }

                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, end = 24.dp)
                    ) {
                        items(count = 5) {
                            ShimmerBox(width = 170.dp, height = 250.dp)
                        }
                    }
                }
            } else {
                item {
                    Column {
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

                        Text(
                            text = cleanTitleText,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = BebasNeue,
                            lineHeight = 38.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val score = detail?.score
                            val scoreText = if (score != null && score > 0) "★ ${String.format("%.1f", score)}" else "98% Coincidencia"
                            Text(scoreText, color = Color(0xFF46D369), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                            Text("${detail?.year ?: 2024}", color = NetflixLightGrey, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)

                            val rawStatus = (detail?.status ?: anime.status ?: "").uppercase()
                            val isReleasing = rawStatus.contains("EMISI") || rawStatus.contains("RELEASING") || rawStatus.contains("ESTRENO")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isReleasing) Color(0xFF2ECC71).copy(alpha = 0.2f) else NetflixDarkGrey)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isMovie) "Película HD" else (if (isReleasing) "En emisión" else "Finalizado"),
                                    color = if (isReleasing && !isMovie) Color(0xFF2ECC71) else NetflixLightGrey,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Inter
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

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

                item {
                    Text(
                        text = cleanSynopsisText,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = 4,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val playButtonText = when {
                            isMovie -> "Reproducir película"
                            currentAnimeHistory != null -> "Continuar Ep. $resumeEpisodeNum"
                            else -> "Reproducir Ep. 1"
                        }

                        Button(
                            onClick = {
                                onPlayEpisode(resumeEpisode)
                            },
                            modifier = Modifier
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties {
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
                                    shape = RoundedCornerShape(6.dp)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = playButtonText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = Inter
                                )
                            }
                        }

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
                                    shape = RoundedCornerShape(6.dp)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (isInMyList) "En mi lista" else "Mi lista",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = Inter
                                )
                            }
                        }
                    }
                }

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

                if (episodeBatches.isNotEmpty() && !isMovie) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items(count = episodeBatches.size) { idx ->
                                val batch = episodeBatches[idx]
                                val startEp = batch.first().number
                                val endEp = batch.last().number
                                val isSelected = selectedBatchIndex == idx

                                Button(
                                    onClick = { selectedBatchIndex = idx },
                                    modifier = Modifier.height(32.dp),
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

                item {
                    val displayEpisodes = if (isMovie) allEpisodes.take(1) else (episodeBatches.getOrNull(selectedBatchIndex) ?: allEpisodes)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(185.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp, end = 24.dp)
                    ) {
                        items(count = displayEpisodes.size) { idx ->
                            val ep = displayEpisodes[idx]
                            val epHistory = watchHistoryList.find { (it.animeSlug == anime.slug || it.animeId == anime.id) && it.episodeNumber == ep.number }
                            val progressFraction = if (epHistory != null && epHistory.durationMs > 0) (epHistory.lastPositionMs.toFloat() / epHistory.durationMs.toFloat()) else null
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
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) { lastPlayedEpNum = ep.number }
                                    }
                            )
                        }
                    }
                }

                if (recommendedList.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Te Recomendamos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = BebasNeue)
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, end = 24.dp)
                        ) {
                            items(count = recommendedList.size) { idx ->
                                val recAnime = recommendedList[idx]
                                com.animetv.app.ui.components.TvAnimeCard(
                                    anime = recAnime,
                                    onClick = { onAnimeSelect?.invoke(recAnime) },
                                    modifier = Modifier.width(170.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
