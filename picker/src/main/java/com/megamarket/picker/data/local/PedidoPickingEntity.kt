package com.megamarket.picker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pedidos_picking")
data class PedidoPickingEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val totalCentimos: Long,
    val fecha: String?,
    val modalidadPago: String?,
    val estadoPago: String?,
    val actualizadoEn: Long
)
