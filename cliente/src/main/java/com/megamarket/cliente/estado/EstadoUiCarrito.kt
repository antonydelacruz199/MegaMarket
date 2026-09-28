package com.megamarket.cliente.estado

import com.megamarket.cliente.modelo.LineaCarrito

data class EstadoUiCarrito(
    val cargando: Boolean = true,
    val lineas: List<LineaCarrito> = emptyList(),
    val subtotalCentimos: Long = 0,
    val totalCentimos: Long = 0,
    val error: String? = null
) {
    val puedeComprar: Boolean
        get() = lineas.any { !it.producto.agotado && it.cantidad >= 1 }
}
