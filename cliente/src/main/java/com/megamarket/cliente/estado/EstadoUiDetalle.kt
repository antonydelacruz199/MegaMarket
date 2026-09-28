package com.megamarket.cliente.estado

import com.megamarket.modelo.Producto

data class EstadoUiDetalle(
    val cargando: Boolean = true,
    val producto: Producto? = null,
    val esFavorito: Boolean = false,
    val cantidadEnCarrito: Int = 0,
    val error: String? = null
)
