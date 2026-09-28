package com.megamarket.app.ui.componentes

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImagenProducto(
    identificador: String,
    modifier: Modifier = Modifier,
    descripcion: String = "Imagen del producto",
    cargar: () -> Bitmap?
) {
    if (identificador.isBlank()) return
    val bitmap by produceState<Bitmap?>(initialValue = null, identificador) {
        value = withContext(Dispatchers.IO) {
            runCatching { cargar() }.getOrNull()
        }
    }
    val imagen = bitmap ?: return
    Image(
        bitmap = imagen.asImageBitmap(),
        contentDescription = descripcion,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}
