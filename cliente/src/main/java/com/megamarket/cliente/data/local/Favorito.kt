package com.megamarket.cliente.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favoritos", primaryKeys = ["ownerId", "productoId"])
data class Favorito(
    val productoId: Long,
    val ownerId: Long = 0
)
