package com.animetv.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.repository.MyListManager
import com.animetv.app.ui.components.NetflixLogo
import com.animetv.app.ui.components.NetflixSideNav
import com.animetv.app.ui.components.TvAnimeCard
import com.animetv.app.ui.theme.BebasNeue
import com.animetv.app.ui.theme.Inter
import com.animetv.app.ui.theme.NetflixBlack
import com.animetv.app.ui.theme.NetflixLightGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyListScreen(
    onAnimeSelect: (AnimeItem) -> Unit,
    onNavigateNav: (String) -> Unit,
    navFocusRequester: FocusRequester,
    searchNavFocusRequester: FocusRequester,
    homeNavFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    var myListAnimes by remember { mutableStateOf(MyListManager.getSavedAnimes(context)) }
    val isLoading = false
    val firstCardFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 72.dp, top = 28.dp, end = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Logo & Title
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NetflixLogo()
                    Text(
                        text = "MI LISTA DE ANIMES",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = BebasNeue,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Subheader
            item {
                Text(
                    text = "Tus animes guardados y favoritos para ver más tarde",
                    color = NetflixLightGrey,
                    fontSize = 15.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium
                )
            }

            // Main Saved Anime List Row / Grid
            item {
                if (isLoading) {
                    Text(
                        text = "Cargando tu lista...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = Inter
                    )
                } else if (myListAnimes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Aún no has agregado animes a tu lista.",
                                color = NetflixLightGrey,
                                fontSize = 15.sp,
                                fontFamily = Inter
                            )
                            Button(
                                onClick = { onNavigateNav("home") },
                                modifier = Modifier
                                    .focusRequester(firstCardFocusRequester)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                navFocusRequester.requestFocus()
                                            }
                                            true // Consume both KeyDown and KeyUp
                                        } else {
                                            false
                                        }
                                    },
                                scale = ButtonDefaults.scale(focusedScale = 1.08f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
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
                                Text("EXPLORAR CATÁLOGO", fontFamily = Inter, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Guardados recientemente (${myListAnimes.size})",
                            color = NetflixLightGrey,
                            fontSize = 16.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        TvLazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp, end = 36.dp)
                        ) {
                            items(myListAnimes) { anime ->
                                val isFirst = anime == myListAnimes.first()
                                val cardModifier = if (isFirst) {
                                    Modifier
                                        .focusRequester(firstCardFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    navFocusRequester.requestFocus()
                                                }
                                                true // Consume both KeyDown and KeyUp
                                            } else {
                                                false
                                            }
                                        }
                                } else Modifier

                                TvAnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeSelect(anime) },
                                    modifier = cardModifier
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlay Side Navigation Bar
        NetflixSideNav(
            selectedId = "mylist",
            onSelect = onNavigateNav,
            onRequestExitNav = {
                try {
                    firstCardFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            searchFocusRequester = searchNavFocusRequester,
            homeFocusRequester = homeNavFocusRequester,
            myListFocusRequester = navFocusRequester,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
