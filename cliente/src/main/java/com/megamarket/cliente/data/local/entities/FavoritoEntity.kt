package com.megamarket.cliente.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favoritos")
data class FavoritoEntity(
    @PrimaryKey val productoId: Long
)
