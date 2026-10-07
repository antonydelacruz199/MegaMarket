package com.megamarket.cliente.data.remote.dto

/**
 * POST /api/pedidos — idempotente por [clientUuid].
 * El backend NO descuenta inventario (lo hacen los movimientos).
 */
data class CrearPedidoRequest(
    val clientUuid: String,
    val totalCentimos: Long,
    val estado: String,
    val detalles: List<PedidoDetalleRequest>,
    val direccion: DireccionPedidoRequest
)

data class PedidoDetalleRequest(
    val productoId: String,
    val nombreProducto: String,
    val precioUnitarioCentimos: Long,
    val cantidad: Int,
    val subtotalCentimos: Long
)

data class DireccionPedidoRequest(
    val departamento: String,
    val provincia: String,
    val distrito: String,
    val direccion: String,
    val telefono: String
)
