package com.megamarket.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megamarket.app.R

@Composable
fun MegaMarketLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    Image(
        painter = painterResource(id = R.drawable.logo_megamarket),
        contentDescription = "Logo MegaMarket",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center
    )
}
