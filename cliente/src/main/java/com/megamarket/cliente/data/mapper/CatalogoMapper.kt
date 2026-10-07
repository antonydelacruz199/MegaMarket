package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.ProductoProviderSnapshot
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

/** [Producto.id] es siempre la PK local del cliente, nunca providerId. */
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

/**
 * Snapshot Provider → entidad Room.
 * [idLocal] 0 = insert (AUTOINCREMENT); si hay fila existente, conservar su id.
 * Nunca: id = snapshot.providerId (salvo migración histórica v5→v6).
 */
fun ProductoProviderSnapshot.toEntity(
    idLocal: Long = 0,
    categoriaIdLocal: Long
): ProductoEntity = ProductoEntity(
    id = idLocal,
    providerId = providerId,
    remoteId = remoteId,
    nombre = nombre,
    marca = marca,
    descripcion = descripcion,
    categoriaId = categoriaIdLocal,
    precioCentimos = precioCentimos,
    precioOfertaCentimos = precioOfertaCentimos,
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo && remoteDeletedAt.isNullOrBlank(),
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

/**
 * DTO remoto → entidad local.
 * [providerIdExistente] se conserva en merge Provider+API; null si es solo API.
 */
fun ProductoDto.aEntidad(
    categoriaIdLocal: Long,
    idLocal: Long = 0,
    providerIdExistente: Long? = null
): ProductoEntity = ProductoEntity(
    id = idLocal,
    providerId = providerIdExistente,
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
