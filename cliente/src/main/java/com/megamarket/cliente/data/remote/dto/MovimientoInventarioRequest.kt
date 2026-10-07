package com.megamarket.cliente.data.remote.dto

/**
 * Contrato futuro POST /api/inventario/movimientos.
 * El backend aplica el delta; Android NO envía stock absoluto.
 */
data class MovimientoInventarioRequest(
    val uuidOperacion: String,
    val productoId: String,
    val tipo: String,
    val cantidad: Int,
    val pedidoUuid: String? = null
)
