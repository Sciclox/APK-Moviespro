package com.animetv.app.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.animetv.app.ui.theme.NetflixRed

data class LucideNavItem(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String
)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NetflixSideNav(
    selectedId: String = "home",
    onSelect: (String) -> Unit,
    onRequestExitNav: (() -> Unit)? = null,
    searchFocusRequester: FocusRequester? = null,
    homeFocusRequester: FocusRequester? = null,
    myListFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val navItems = remember {
        listOf(
            LucideNavItem("search", Icons.Default.Search, "Buscar"),
            LucideNavItem("home", Icons.Default.Home, "Inicio"),
            LucideNavItem("mylist", Icons.Default.BookmarkBorder, "Mi Lista")
        )
    }

    var isAnyFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(
                if (isAnyFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            navItems.forEach { item ->
                val isSelected = item.id == selectedId
                var isFocused by remember { mutableStateOf(false) }

                val itemFocusRequester = when (item.id) {
                    "search" -> searchFocusRequester
                    "home" -> homeFocusRequester
                    "mylist" -> myListFocusRequester
                    else -> null
                }

                val boxModifier = Modifier
                    .focusProperties {
                        // Explicitly cancel 2D focus search to the left to prevent wrap-around jumps into page content
                        left = FocusRequester.Cancel
                    }
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            isAnyFocused = true
                        }
                    }
                    .run {
                        if (itemFocusRequester != null) {
                            this.focusRequester(itemFocusRequester)
                        } else {
                            this
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val keyCode = keyEvent.nativeKeyEvent.keyCode
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    if (selectedId != item.id) {
                                        onSelect(item.id)
                                    }
                                }
                                true // Consume OK/ENTER: ONLY key that changes page
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    onRequestExitNav?.invoke()
                                }
                                true // Consume D-Pad Right: ONLY key that exits navbar to page content
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                true // Disable D-Pad Left inside navbar completely
                            }
                            else -> false // Allow D-Pad Up and Down to move focus vertically through navbar options
                        }
                    }
                    .focusable()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (selectedId != item.id) {
                            onSelect(item.id)
                        }
                        onRequestExitNav?.invoke()
                    }
                    .padding(vertical = 14.dp)

                Box(
                    modifier = boxModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // White Vector Icon
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            tint = if (isFocused || isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        // Netflix Red Line Indicator when Focused or White Line when Selected
                        if (isFocused) {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(NetflixRed)
                            )
                        } else if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(Color.White.copy(alpha = 0.7f))
                            )
                        } else {
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            }
        }
    }
}
