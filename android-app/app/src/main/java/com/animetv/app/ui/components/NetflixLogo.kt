package com.animetv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.animetv.app.R

@Composable
fun NetflixLogo(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.limotion_logo),
        contentDescription = "Limotion Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(36.dp)
    )
}
