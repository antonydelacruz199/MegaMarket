package com.megamarket.cliente.model

data class Pedido(
    val id: Long,
    val clienteId: Long,
    val fecha: Long,
    val totalCentimos: Long,
    val estado: String,
    val detalles: List<PedidoDetalle>,
    val direccion: Direccion
) {
    val cantidadProductos: Int
        get() = detalles.sumOf { it.cantidad }

    companion object {
        const val ESTADO_CONFIRMADO = "CONFIRMADO"
    }
}
