package com.megamarket.app.data.remote.dto

data class MovimientoInventarioRequest(
    val uuidOperacion: String,
    val productoId: String,
    val tipo: String,
    val cantidad: Int,
    val pedidoUuid: String? = null
)
