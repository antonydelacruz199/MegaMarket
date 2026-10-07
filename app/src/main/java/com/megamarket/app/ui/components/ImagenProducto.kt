package com.megamarket.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
    identificador: String,
    modifier: Modifier = Modifier,
    descripcion: String = "Imagen del producto",
    cargar: suspend () -> Bitmap?
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, identificador) {
        value = if (identificador.isBlank()) null else cargar()
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
        }
    }
}
