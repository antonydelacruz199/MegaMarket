package com.megamarket.cliente.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "carrito",
    indices = [Index(value = ["ownerId", "productoId"], unique = true)]
)
data class CarritoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productoId: Long,
    @androidx.room.ColumnInfo(defaultValue = "0") val ownerId: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "''") val cartUuid: String = "",
    val cantidad: Int
)
