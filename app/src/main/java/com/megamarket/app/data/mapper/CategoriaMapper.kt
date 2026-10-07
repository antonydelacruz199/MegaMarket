package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.entities.CategoriaEntity
import com.megamarket.modelo.Categoria

fun CategoriaEntity.toModel(): Categoria = Categoria(
    id = id,
    nombre = nombre,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

fun Categoria.toEntity(): CategoriaEntity = CategoriaEntity(
    id = id,
    remoteId = remoteId,
    nombre = nombre,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)
