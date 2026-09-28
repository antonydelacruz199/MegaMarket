package com.megamarket.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "productos")
data class EntidadProducto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long?,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean
)
