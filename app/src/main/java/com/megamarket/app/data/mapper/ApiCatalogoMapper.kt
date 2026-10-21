package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.Categoria
import com.megamarket.app.data.local.Producto
import com.megamarket.app.data.remote.ActualizarProductoRequest
import com.megamarket.app.data.remote.CategoriaApiDto
import com.megamarket.app.data.remote.CrearProductoRequest
import com.megamarket.app.data.remote.ProductoApiDto
import com.megamarket.modelo.PrecioDescuento

fun CategoriaApiDto.aEntidadLocal(idLocal: Long = 0): Categoria =
    Categoria(
        id = idLocal,
        remoteId = id,
        nombre = nombre.trim(),
        remoteVersion = version,
        remoteUpdatedAt = updatedAt,
        remoteDeletedAt = null
    )

fun ProductoApiDto.aEntidadLocal(idLocal: Long, categoriaIdLocal: Long): Producto {
    val descuento = if (esOferta) {
        PrecioDescuento.descuentoDesdePrecioOferta(precioCentimos, precioOfertaCentimos)
    } else {
        0
    }
    return Producto(
        id = idLocal,
        remoteId = id,
        nombre = nombre.trim(),
        marca = marca.trim(),
        descripcion = descripcion.orEmpty(),
        categoriaId = categoriaIdLocal,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = precioOfertaCentimos,
        descuentoPorcentaje = descuento,
        stock = stock,
        imagenKey = imagenKey.orEmpty(),
        esOferta = esOferta,
        activo = activo,
        remoteVersion = version,
        remoteUpdatedAt = updatedAt,
        remoteDeletedAt = null
    )
}

fun Producto.aCrearProductoRequest(categoriaRemotaId: String, uuidOperacion: String): CrearProductoRequest =
    CrearProductoRequest(
        nombre = nombre.trim(),
        marca = marca.trim(),
        descripcion = descripcion,
        categoriaId = categoriaRemotaId,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = if (esOferta) precioOfertaCentimos else null,
        stock = stock,
        imagenKey = imagenKeyParaApi(imagenKey),
        esOferta = esOferta,
        activo = activo,
        uuidOperacion = uuidOperacion
    )

fun Producto.aActualizarProductoRequest(
    categoriaRemotaId: String,
    uuidOperacion: String
): ActualizarProductoRequest =
    ActualizarProductoRequest(
        nombre = nombre.trim(),
        marca = marca.trim(),
        descripcion = descripcion,
        categoriaId = categoriaRemotaId,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = if (esOferta) precioOfertaCentimos else null,
        esOferta = esOferta,
        activo = activo,
        imagenKey = imagenKeyParaApi(imagenKey),
        version = remoteVersion ?: 1L,
        uuidOperacion = uuidOperacion
    )

/** No envía rutas locales de Android como si fueran claves remotas. */
fun imagenKeyParaApi(imagenKey: String?): String? {
    val key = imagenKey?.trim().orEmpty()
    if (key.isEmpty()) return null
    if (key.startsWith("/") ||
        key.startsWith("content:") ||
        key.startsWith("file:") ||
        key.contains("://") ||
        key.contains('\\')
    ) {
        return null
    }
    return key
}
