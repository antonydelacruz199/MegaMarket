package com.megamarket.cliente.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun ImagenProducto(
    productoId: Long,
    imagenKey: String,
    cargarImagen: suspend (productoId: Long, lado: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    lado: Int = 480,
    descripcion: String = "Imagen del producto"
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, productoId, imagenKey) {
        value = if (imagenKey.isBlank()) null else cargarImagen(productoId, lado)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val imagen = bitmap
        if (imagen != null) {
            Image(
                bitmap = imagen.asImageBitmap(),
                contentDescription = descripcion,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = descripcion,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
