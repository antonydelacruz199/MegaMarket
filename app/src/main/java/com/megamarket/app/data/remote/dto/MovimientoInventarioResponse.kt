package com.megamarket.app.data.remote.dto

data class MovimientoInventarioResponse(
    val id: String? = null,
    val uuidOperacion: String? = null,
    val productoId: String? = null,
    val stockActual: Int? = null,
    val version: Long? = null,
    val createdAt: String? = null,
    val duplicado: Boolean? = null
)
