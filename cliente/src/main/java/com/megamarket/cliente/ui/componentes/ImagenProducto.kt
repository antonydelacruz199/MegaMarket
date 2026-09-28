package com.megamarket.cliente.ui.componentes

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.decodificarImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImagenProducto(
    productoId: Long,
    imagenKey: String,
    modifier: Modifier = Modifier,
    lado: Int = 480,
    descripcion: String = "Imagen del producto"
) {
    if (imagenKey.isBlank()) return
    val contexto = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, productoId, imagenKey) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                contexto.contentResolver.openInputStream(ContratoCatalogo.uriImagen(productoId))
                    ?.use { entrada -> entrada.readBytes().decodificarImagen(lado) }
            }.getOrNull()
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
