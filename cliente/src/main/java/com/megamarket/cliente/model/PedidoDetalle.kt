package com.megamarket.cliente.model

data class PedidoDetalle(
    val id: Long = 0,
    val productoId: Long,
    val nombreProducto: String,
    val precioUnitarioCentimos: Long,
    val cantidad: Int,
    val subtotalCentimos: Long
)
