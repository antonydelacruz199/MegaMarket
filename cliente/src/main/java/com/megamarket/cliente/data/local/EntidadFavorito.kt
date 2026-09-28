package com.megamarket.cliente.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favoritos")
data class EntidadFavorito(
    @PrimaryKey val productoId: Long
)
