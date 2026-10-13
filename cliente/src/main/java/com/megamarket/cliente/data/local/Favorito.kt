package com.megamarket.cliente.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favoritos")
data class Favorito(
    @PrimaryKey val productoId: Long
)
