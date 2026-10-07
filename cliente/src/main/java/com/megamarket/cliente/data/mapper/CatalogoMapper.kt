package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.remote.dto.CategoriaDto
import com.megamarket.cliente.data.remote.dto.ProductoDto
import com.megamarket.modelo.Categoria
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
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

fun Producto.toEntity(): ProductoEntity = ProductoEntity(
    id = id,
    remoteId = remoteId,
    nombre = nombre,
    marca = marca,
    descripcion = descripcion,
    categoriaId = categoriaId,
    precioCentimos = precioCentimos,
    precioOfertaCentimos = precioOfertaCentimos,
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

/**
 * Mapea DTO remoto → entidad local.
 * [categoriaIdLocal] debe resolverse antes buscando por remoteId de categoría.
 * Nunca inventa UUID: remoteId = dto.id.
 */
fun ProductoDto.aEntidad(categoriaIdLocal: Long, idLocal: Long? = null): ProductoEntity =
    ProductoEntity(
        id = idLocal ?: 0L,
        remoteId = id,
        nombre = nombre,
        marca = marca,
        descripcion = descripcion,
        categoriaId = categoriaIdLocal,
        precioCentimos = precioCentimos,
        precioOfertaCentimos = precioOfertaCentimos,
        stock = stock,
        imagenKey = imagenKey,
        esOferta = esOferta,
        activo = activo && deletedAt.isNullOrBlank(),
        remoteVersion = version,
        remoteUpdatedAt = updatedAt,
        remoteDeletedAt = deletedAt
    )

fun CategoriaDto.aEntidad(idLocal: Long? = null): CategoriaEntity = CategoriaEntity(
    id = idLocal ?: 0L,
    remoteId = id,
    nombre = nombre,
    remoteVersion = version,
    remoteUpdatedAt = updatedAt,
    remoteDeletedAt = deletedAt
)

/**
 * Convierte UUID de categoría remota → id local Long.
 * Nunca guarda el UUID en categoriaId.
 */
fun resolverCategoriaIdLocal(
    categoriaRemoteId: String,
    categoriasLocales: List<CategoriaEntity>
): Long? = categoriasLocales.firstOrNull { it.remoteId == categoriaRemoteId }?.id
