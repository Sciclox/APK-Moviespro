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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetflixSideNav(
    selectedId: String = "home",
    onSelect: (String) -> Unit,
    onRequestExitNav: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val navItems = remember {
        listOf(
            LucideNavItem("search", Icons.Default.Search, "Buscar"),
            LucideNavItem("home", Icons.Default.Home, "Inicio"),
            LucideNavItem("mylist", Icons.Default.BookmarkBorder, "Mi Lista")
        )
    }

    Column(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(Color.Transparent)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        navItems.forEach { item ->
            val isSelected = item.id == selectedId
            var isFocused by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        // Auto-change screen on simply focusing up/down
                        if (focusState.isFocused && selectedId != item.id) {
                            onSelect(item.id)
                        }
                    }
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        // Pressing D-Pad RIGHT ➡️ exits the side navbar to the main page content
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            onRequestExitNav?.invoke()
                            true
                        } else {
                            false
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSelect(item.id)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // White Lucide Vector Icon
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Red Line below the icon matching icon width (18.dp)
                    if (isFocused || isSelected) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.25.dp))
                                .background(NetflixRed)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.5.dp))
                    }
                }
            }
        }
    }
}
