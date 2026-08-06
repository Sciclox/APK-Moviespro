package com.animetv.app.ui.components

import android.graphics.drawable.BitmapDrawable
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.tv.material3.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixLightGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroBanner(
    heroList: List<AnimeItem>,
    onAnimeSelect: (AnimeItem) -> Unit,
    onFocusItem: (FocusRequester) -> Unit,
    firstKeyFocusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    scrollToRow: (Int) -> Unit,
    categoryBadgeText: String = "A N I M E   O R I G I N A L",
    modifier: Modifier = Modifier
) {
    if (heroList.isEmpty()) return

    val context = LocalContext.current

    val cleanHeroList = remember(heroList) {
        val withBackdrop = heroList.filter { !it.backdrop.isNullOrEmpty() }
        if (withBackdrop.isNotEmpty()) withBackdrop.take(5) else heroList.take(5)
    }

    var currentHeroIndex by remember { mutableIntStateOf(0) }
    var isHeroFocused by remember { mutableStateOf(false) }

    // Auto-rotate Hero Banner every 7 seconds when not focused
    LaunchedEffect(cleanHeroList, isHeroFocused) {
        if (cleanHeroList.size > 1 && !isHeroFocused) {
            while (true) {
                delay(7000)
                currentHeroIndex = (currentHeroIndex + 1) % cleanHeroList.size
            }
        }
    }

    val activeHero = cleanHeroList.getOrNull(currentHeroIndex) ?: cleanHeroList.firstOrNull()

    // Automatic TMDB Backdrop resolver if backdrop is missing in catalog
    var activeHeroBackdrop by remember(activeHero?.id) { mutableStateOf(activeHero?.backdrop) }

    LaunchedEffect(activeHero?.id) {
        if (activeHero != null && (activeHeroBackdrop.isNullOrEmpty() || activeHeroBackdrop == activeHero.image)) {
            try {
                val details = ApiClient.apiService.getAnimeDetails(activeHero.slug)
                if (details.success && !details.data.backdrop.isNullOrEmpty()) {
                    activeHeroBackdrop = details.data.backdrop
                }
            } catch (_: Exception) {}
        }
    }

    var dominantColor by remember { mutableStateOf(Color(0xFFE50914)) }
    val animatedAmbientColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientColor"
    )

    val heroImageUrl = activeHeroBackdrop?.ifEmpty { null } ?: activeHero?.backdrop?.ifEmpty { null } ?: ""

    // Dynamic color extraction for ambient resplandor glow (Offloaded to Dispatchers.Default)
    LaunchedEffect(heroImageUrl) {
        if (heroImageUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(heroImageUrl)
                    .allowHardware(false)
                    .target { drawable ->
                        val bitmap = (drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null && !bitmap.isRecycled) {
                            Palette.from(bitmap).generate { palette ->
                                val vibrant = palette?.dominantSwatch?.rgb ?: palette?.vibrantSwatch?.rgb
                                vibrant?.let {
                                    val hsl = FloatArray(3)
                                    ColorUtils.colorToHSL(it, hsl)
                                    hsl[2] = 0.55f
                                    val brightColor = Color(ColorUtils.HSLToColor(hsl))
                                    dominantColor = brightColor
                                }
                            }
                        }
                    }
                    .build()
                loader.enqueue(req)
            }
        }
    }

    if (activeHero != null) {
        val heroShape = RoundedCornerShape(16.dp)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 36.dp, bottom = 16.dp)
                .height(380.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = heroShape,
                    ambientColor = if (animatedAmbientColor != Color.Transparent) animatedAmbientColor.copy(alpha = 0.35f) else Color.Black,
                    spotColor = if (animatedAmbientColor != Color.Transparent) animatedAmbientColor.copy(alpha = 0.45f) else Color.Black
                )
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    shape = heroShape
                    clip = true
                }
                .background(Color.Black)
        ) {
            Crossfade(
                targetState = heroImageUrl,
                animationSpec = tween(durationMillis = 300),
                label = "heroCrossfade"
            ) { targetUrl ->
                key(targetUrl) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(targetUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .addHeader("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                            .crossfade(true)
                            .build(),
                        contentDescription = activeHero.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Dynamic shadow gradient directed upwards
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                animatedAmbientColor.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.70f),
                                Color.Black.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            // Text Content and Action Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 24.dp, end = 24.dp)
                    .width(480.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NetflixLogo(modifier = Modifier.size(16.dp))
                    Text(
                        text = categoryBadgeText,
                        color = NetflixLightGrey,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = activeHero.title,
                    color = Color.White,
                    fontSize = 44.sp,
                    fontFamily = BebasNeue,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 46.sp,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = activeHero.synopsis ?: "Disfruta de esta increíble producción en calidad Full HD.",
                    color = NetflixLightGrey,
                    fontSize = 13.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAnimeSelect(activeHero) },
                        modifier = Modifier
                            .focusRequester(firstKeyFocusRequester)
                            .onFocusChanged { focusState ->
                                isHeroFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    onFocusItem(firstKeyFocusRequester)
                                    scrollToRow(0)
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            try { navFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
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
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "▶ Reproducir",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onAnimeSelect(activeHero) },
                        modifier = Modifier
                            .onFocusChanged { focusState ->
                                isHeroFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    onFocusItem(firstKeyFocusRequester)
                                    scrollToRow(0)
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && keyEvent.type == KeyEventType.KeyDown) {
                                    try { navFocusRequester.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
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
                        Text(
                            text = "Más información",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
