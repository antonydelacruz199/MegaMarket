package com.megamarket.cliente.model.estado

import com.megamarket.cliente.model.LineaCarrito

data class CarritoUiState(
    val cargando: Boolean = true,
    val lineas: List<LineaCarrito> = emptyList(),
    val subtotalCentimos: Long = 0,
    val totalCentimos: Long = 0,
    val error: String? = null
) {
    val puedeComprar: Boolean
        get() = lineas.isNotEmpty() && lineas.all { it.comprable }
}
