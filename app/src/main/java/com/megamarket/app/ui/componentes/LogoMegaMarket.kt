package com.megamarket.app.ui.componentes

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
fun LogoMegaMarket(
    modifier: Modifier = Modifier,
    tamano: Dp = 72.dp
) {
    Image(
        painter = painterResource(id = R.drawable.logo_megamarket),
        contentDescription = "Logo MegaMarket",
        modifier = modifier.size(tamano),
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center
    )
}
