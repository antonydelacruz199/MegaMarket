package com.megamarket.cliente.ui.components

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
import com.megamarket.modelo.SolicitudImagenProducto

@Composable
fun ImagenProducto(
    solicitud: SolicitudImagenProducto?,
    modifier: Modifier = Modifier,
    descripcion: String = "Imagen del producto"
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (solicitud == null) {
            IconoImagen(Icons.Default.ShoppingCart, descripcion)
            return
        }
        val pedido = ImageRequest.Builder(LocalContext.current)
            .data(solicitud.url)
            .memoryCacheKey(solicitud.cacheKey)
            .diskCacheKey(solicitud.cacheKey)
            .build()
        SubcomposeAsyncImage(
            model = pedido,
            contentDescription = descripcion,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { IndicadorCarga() },
            error = { IconoImagen(Icons.Default.Warning, "No se pudo cargar la imagen") }
        )
    }
}

@Composable
private fun IndicadorCarga() {
    CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        strokeWidth = 2.dp
    )
}

@Composable
private fun IconoImagen(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String
) {
    Icon(
        imageVector = icono,
        contentDescription = descripcion,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
