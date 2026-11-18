package com.megamarket.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CancellationException
import coil3.request.ImageRequest
import com.megamarket.modelo.ClavesImagen
import com.megamarket.modelo.SolicitudImagenProducto

@Composable
fun ImagenProducto(
    clave: String,
    solicitud: SolicitudImagenProducto?,
    modifier: Modifier = Modifier,
    descripcion: String = "Imagen del producto",
    ladoLocal: Int = 240,
    cargarLocal: suspend (clave: String, lado: Int) -> Bitmap? = { _, _ -> null }
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            clave.isBlank() -> Icono(Icons.Default.ShoppingCart, "Sin fotografía")
            ClavesImagen.esLocal(clave) -> ImagenLocal(clave, ladoLocal, descripcion, cargarLocal)
            solicitud != null -> ImagenRemota(solicitud, descripcion)
            else -> Icono(Icons.Default.ShoppingCart, "Sin fotografía")
        }
    }
}

@Composable
private fun ImagenLocal(
    clave: String,
    lado: Int,
    descripcion: String,
    cargarLocal: suspend (clave: String, lado: Int) -> Bitmap?
) {
    val estado by produceState<EstadoLocal>(EstadoLocal.Cargando, clave, lado) {
        value = try {
            val bitmap = cargarLocal(clave, lado)
            if (bitmap == null) EstadoLocal.Error else EstadoLocal.Lista(bitmap)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            EstadoLocal.Error
        }
    }
    when (val actual = estado) {
        EstadoLocal.Cargando -> Indicador()
        EstadoLocal.Error -> Icono(Icons.Default.Warning, "No se pudo cargar la imagen")
        is EstadoLocal.Lista -> Image(
            bitmap = actual.bitmap.asImageBitmap(),
            contentDescription = descripcion,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ImagenRemota(solicitud: SolicitudImagenProducto, descripcion: String) {
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
        loading = { Indicador() },
        error = { Icono(Icons.Default.Warning, "No se pudo cargar la imagen") }
    )
}

@Composable
private fun Indicador() {
    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
}

@Composable
private fun Icono(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String
) {
    Icon(
        imageVector = icono,
        contentDescription = descripcion,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private sealed interface EstadoLocal {
    data object Cargando : EstadoLocal
    data object Error : EstadoLocal
    data class Lista(val bitmap: Bitmap) : EstadoLocal
}
