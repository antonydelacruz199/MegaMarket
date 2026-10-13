package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.Categoria as CategoriaRoom
import com.megamarket.modelo.Categoria

fun CategoriaRoom.toModel(): Categoria = Categoria(
    id = id,
    nombre = nombre,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)

fun Categoria.toEntity(): CategoriaRoom = CategoriaRoom(
    id = id,
    remoteId = remoteId,
    nombre = nombre,
    remoteVersion = remoteVersion,
    remoteUpdatedAt = remoteUpdatedAt,
    remoteDeletedAt = remoteDeletedAt
)
