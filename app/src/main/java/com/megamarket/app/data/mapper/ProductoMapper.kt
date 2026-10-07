package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.entities.ProductoEntity
import com.megamarket.modelo.PrecioDescuento
import com.megamarket.modelo.Producto

fun Producto.toEntity(): ProductoEntity {
    val descuento = if (esOferta) descuentoPorcentaje.coerceIn(0, 100) else 0
    val ofertaCentimos = PrecioDescuento.precioOfertaDesdeDescuento(precioCentimos, descuento)
    return ProductoEntity(
        id = id,
        remoteId = remoteId,
        nombre = nombre,
        marca = marca,
        descripcion = descripcion,
        categoriaId = categoriaId,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = ofertaCentimos,
        descuentoPorcentaje = descuento,
        stock = stock,
        imagenKey = imagenKey,
        esOferta = esOferta,
        activo = activo,
        remoteVersion = remoteVersion,
        remoteUpdatedAt = remoteUpdatedAt,
        remoteDeletedAt = remoteDeletedAt
    )
}

fun ProductoEntity.toModel(): Producto = Producto(
    id = id,
    nombre = nombre,
    marca = marca,
    descripcion = descripcion,
    categoriaId = categoriaId,
    precioCentimos = precioCentimos,
    precioOfertaCentimos = precioOfertaCentimos,
    descuentoPorcentaje = descuentoPorcentaje,
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)
