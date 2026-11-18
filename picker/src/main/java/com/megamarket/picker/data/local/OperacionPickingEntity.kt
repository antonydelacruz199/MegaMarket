package com.megamarket.picker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "operaciones_pendientes",
    indices = [
        Index(value = ["uuidOperacion"], unique = true),
        Index(value = ["estado"]),
        Index(value = ["ownerId", "estado"]),
        Index(value = ["detalleId", "operacion", "estado"])
    ]
)
data class OperacionPickingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuidOperacion: String,
    val ownerId: String,
    val pedidoId: String,
    val detalleId: String,
    val operacion: String,
    val codigoBarras: String?,
    val cantidad: Int,
    val estado: String = OperacionPicking.PENDIENTE,
    val resultado: String? = null,
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val fechaCreacion: Long,
    val fechaActualizacion: Long = fechaCreacion,
    val sincronizadoEn: Long? = null
)
