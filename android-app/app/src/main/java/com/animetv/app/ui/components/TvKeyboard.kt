package com.animetv.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val row1 = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M")
    val row2 = listOf("N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")
    val row3 = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Row 1: A-M
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row1.forEach { letter ->
                KeyButton(
                    text = letter,
                    onClick = { onKeyClick(letter) },
                    focusRequester = if (letter == "A") firstKeyFocusRequester else null
                )
            }
        }

        // Row 2: N-Z
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row2.forEach { letter ->
                KeyButton(text = letter, onClick = { onKeyClick(letter) })
            }
        }

        // Row 3: Numbers 0-9
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row3.forEach { number ->
                KeyButton(text = number, onClick = { onKeyClick(number) })
            }
        }

        // Row 4: Action Keys
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(text = "␣ ESPACIO", onClick = { onKeyClick(" ") }, width = 120.dp)
            KeyButton(text = "⌫ BORRAR", onClick = onBackspace, width = 110.dp)
            KeyButton(text = "❌ LIMPIAR", onClick = onClear, width = 110.dp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyButton(
    text: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 34.dp,
    focusRequester: FocusRequester? = null
) {
    val modifier = if (focusRequester != null) {
        Modifier.width(width).height(38.dp).focusRequester(focusRequester)
    } else {
        Modifier.width(width).height(38.dp)
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.12f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.18f),
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
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
