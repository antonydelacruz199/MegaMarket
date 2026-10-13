package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.ProductoProviderSnapshot
import com.megamarket.cliente.data.local.Categoria as CategoriaRoom
import com.megamarket.cliente.data.local.Producto as ProductoRoom
import com.megamarket.modelo.Categoria
import com.megamarket.modelo.PrecioDescuento
import com.megamarket.modelo.Producto

fun CategoriaRoom.toModel(): Categoria = Categoria(
    id = id,
    nombre = nombre,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

fun ProductoRoom.toModel(): Producto = Producto(
    id = id,
    nombre = nombre,
    marca = marca,
    descripcion = descripcion,
    categoriaId = categoriaId,
    precioCentimos = precioCentimos,
    precioOfertaCentimos = precioOfertaCentimos,
    descuentoPorcentaje = descuentoPorcentaje.takeIf { it > 0 }
        ?: PrecioDescuento.descuentoDesdePrecioOferta(precioCentimos, precioOfertaCentimos),
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

fun ProductoProviderSnapshot.toEntity(
    idLocal: Long = 0,
    categoriaIdLocal: Long
): ProductoRoom {
    val descuento = when {
        descuentoPorcentaje > 0 -> descuentoPorcentaje
        esOferta -> PrecioDescuento.descuentoDesdePrecioOferta(precioCentimos, precioOfertaCentimos)
        else -> 0
    }
    val oferta = PrecioDescuento.precioOfertaDesdeDescuento(precioCentimos, descuento)
        ?: precioOfertaCentimos
    return ProductoRoom(
        id = idLocal,
        providerId = providerId,
        remoteId = remoteId,
        nombre = nombre,
        marca = marca,
        descripcion = descripcion,
        categoriaId = categoriaIdLocal,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = oferta,
        descuentoPorcentaje = descuento,
        stock = stock,
        imagenKey = imagenKey,
        esOferta = esOferta && descuento in 1..99,
        activo = activo && remoteDeletedAt.isNullOrBlank(),
        remoteVersion = remoteVersion,
        remoteUpdatedAt = remoteUpdatedAt,
        remoteDeletedAt = remoteDeletedAt
    )
}
