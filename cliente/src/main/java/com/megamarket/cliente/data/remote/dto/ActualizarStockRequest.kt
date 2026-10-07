package com.megamarket.cliente.data.remote.dto

/**
 * Body para PATCH /api/productos/{uuid}/stock
 *
 * Endpoint REST pendiente de conectar cuando exista la API y el UUID remoto.
 * Android NUNCA habla con Neon/PostgreSQL directamente.
 */
data class ActualizarStockRequest(
    val stock: Int
)
