package com.megamarket.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brand logo placeholder.
 * TODO: reemplazar por R.drawable.logo_megamarket
 * Expected path: app/src/main/res/drawable-nodpi/logo_megamarket.png
 */
@Composable
fun MegaMarketLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    tint: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = "Logo MegaMarket",
            tint = tint,
            modifier = Modifier.size(size * 0.7f)
        )
    }
}
