package com.megamarket.cliente.model.estado

import com.megamarket.modelo.Producto

data class DetalleProductoUiState(
    val cargando: Boolean = true,
    val producto: Producto? = null,
    val esFavorito: Boolean = false,
    val cantidadEnCarrito: Int = 0,
    val error: String? = null
)
