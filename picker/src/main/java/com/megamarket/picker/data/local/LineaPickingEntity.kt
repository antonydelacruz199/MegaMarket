package com.megamarket.picker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lineas_picking",
    indices = [Index(value = ["pedidoId"]), Index(value = ["ownerId"])]
)
data class LineaPickingEntity(
    @PrimaryKey val id: String,
    val pedidoId: String,
    val ownerId: String,
    val productoId: String,
    val nombre: String,
    val cantidad: Int,
    val cantidadArmada: Int,
    val estadoPicking: String,
    val pasillo: String?,
    val codigoBarras: String?,
    val actualizadoEn: Long,
    val sustitucionAceptadaId: String? = null,
    val productoSustitutoId: String? = null,
    val nombreSustituto: String? = null,
    val resolubleSinProducto: Boolean = false
)
