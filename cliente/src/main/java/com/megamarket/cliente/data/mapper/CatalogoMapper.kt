package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.ProductoProviderSnapshot
import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.remote.dto.CategoriaDto
import com.megamarket.cliente.data.remote.dto.ProductoDto
import com.megamarket.modelo.Categoria
import com.megamarket.modelo.PrecioDescuento
import com.megamarket.modelo.Producto

fun CategoriaEntity.toModel(): Categoria = Categoria(
    id = id,
    nombre = nombre,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

fun ProductoEntity.toModel(): Producto = Producto(
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
): ProductoEntity {
    val descuento = when {
        descuentoPorcentaje > 0 -> descuentoPorcentaje
        esOferta -> PrecioDescuento.descuentoDesdePrecioOferta(precioCentimos, precioOfertaCentimos)
        else -> 0
    }
    val oferta = PrecioDescuento.precioOfertaDesdeDescuento(precioCentimos, descuento)
        ?: precioOfertaCentimos
    return ProductoEntity(
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

fun ProductoDto.aEntidad(
    categoriaIdLocal: Long,
    idLocal: Long = 0,
    providerIdExistente: Long? = null
): ProductoEntity {
    val descuento = when {
        descuentoPorcentaje > 0 -> descuentoPorcentaje
        else -> PrecioDescuento.descuentoDesdePrecioOferta(precioCentimos, precioOfertaCentimos)
    }
    return ProductoEntity(
        id = idLocal,
        providerId = providerIdExistente,
        remoteId = id,
        nombre = nombre,
        marca = marca,
        descripcion = descripcion,
        categoriaId = categoriaIdLocal,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = precioOfertaCentimos,
        descuentoPorcentaje = descuento,
        stock = stock,
        imagenKey = imagenKey,
        esOferta = esOferta,
        activo = activo && deletedAt.isNullOrBlank(),
        remoteVersion = version,
        remoteUpdatedAt = updatedAt,
        remoteDeletedAt = deletedAt
    )
}

fun CategoriaDto.aEntidad(idLocal: Long? = null): CategoriaEntity = CategoriaEntity(
    id = idLocal ?: 0L,
    remoteId = id,
    nombre = nombre,
    remoteVersion = version,
    remoteUpdatedAt = updatedAt,
    remoteDeletedAt = deletedAt
)

fun resolverCategoriaIdLocal(
    categoriaRemoteId: String,
    categoriasLocales: List<CategoriaEntity>
): Long? = categoriasLocales.firstOrNull { it.remoteId == categoriaRemoteId }?.id
