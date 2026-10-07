package com.megamarket.cliente.data.remote.dto

data class MovimientoInventarioResponse(
    val id: String? = null,
    val uuidOperacion: String,
    val productoId: String,
    val stockActual: Int,
    val version: Long? = null,
    val createdAt: String? = null,
    val duplicado: Boolean? = null
)
