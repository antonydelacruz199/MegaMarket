package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.Categoria
import com.megamarket.cliente.data.local.Producto
import com.megamarket.cliente.data.remote.CategoriaApiDto
import com.megamarket.cliente.data.remote.ProductoApiDto
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
        providerId = null,
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
