package com.megamarket.cliente.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "carrito",
    indices = [Index(value = ["productoId"], unique = true)]
)
data class EntidadCarrito(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productoId: Long,
    val cantidad: Int
)
