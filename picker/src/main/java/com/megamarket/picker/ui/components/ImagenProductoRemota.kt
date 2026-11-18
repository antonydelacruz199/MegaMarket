package com.megamarket.picker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

@Composable
fun ImagenProductoRemota(
    url: String?,
    descripcion: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = "Sin fotografía",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Box
        }
        val pedido = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .build()
        SubcomposeAsyncImage(
            model = pedido,
            contentDescription = descripcion,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            },
            error = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "No se pudo cargar la imagen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
