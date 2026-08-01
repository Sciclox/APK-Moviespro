package com.animetv.app.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.animetv.app.ui.theme.Inter

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvKeyboard(
    onKeyClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    firstKeyFocusRequester: FocusRequester? = null,
    navFocusRequester: FocusRequester? = null,
    onRightDpad: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val row1 = listOf("a", "b", "c", "d", "e", "f")
    val row2 = listOf("g", "h", "i", "j", "k", "l")
    val row3 = listOf("m", "n", "o", "p", "q", "r")
    val row4 = listOf("s", "t", "u", "v", "w", "x")
    val row5 = listOf("y", "z", "1", "2", "3", "4")
    val row6 = listOf("5", "6", "7", "8", "9", "0")

    val keySize = 34.dp
    val spacing = 4.dp

    Column(
        modifier = modifier
            .padding(top = 0.dp, bottom = 4.dp)
            .graphicsLayer { clip = false },
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.Start
    ) {
        // Top Action Row: Space & Delete with White Vector Icons (Total width = 110 + 4 + 110 = 224.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.graphicsLayer { clip = false }
        ) {
            KeyButton(
                icon = Icons.Default.SpaceBar,
                contentDescription = "Espacio",
                onClick = { onKeyClick(" ") },
                width = 110.dp,
                height = 34.dp,
                onUpDpad = { navFocusRequester?.requestFocus() }
            )
            KeyButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Borrar",
                onClick = onBackspace,
                width = 110.dp,
                height = 34.dp,
                onRightDpad = onRightDpad,
                onUpDpad = { navFocusRequester?.requestFocus() }
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Grid Rows (6x6 compact 1:1 square keys = 224.dp total width)
        val allRows = listOf(row1, row2, row3, row4, row5, row6)
        allRows.forEach { rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.graphicsLayer { clip = false }
            ) {
                rowKeys.forEach { key ->
                    val isLastInRow = key == rowKeys.last()

                    KeyButton(
                        text = key,
                        onClick = { onKeyClick(key) },
                        width = keySize,
                        height = keySize,
                        focusRequester = if (key == "a") firstKeyFocusRequester else null,
                        onRightDpad = if (isLastInRow) onRightDpad else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyButton(
    text: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    onClick: () -> Unit,
    width: Dp = 34.dp,
    height: Dp = 34.dp,
    focusRequester: FocusRequester? = null,
    onRightDpad: (() -> Unit)? = null,
    onUpDpad: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    var modifier = Modifier
        .width(width)
        .height(height)
        .onFocusChanged { isFocused = it.isFocused }

    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }

    if (onRightDpad != null || onUpDpad != null) {
        modifier = modifier.onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (onRightDpad != null) {
                            onRightDpad()
                            return@onKeyEvent true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (onUpDpad != null) {
                            onUpDpad()
                            return@onKeyEvent true
                        }
                    }
                }
            }
            false
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(6.dp)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else if (text != null) {
                Text(
                    text = text,
                    color = if (isFocused) Color.Black else Color.White,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
