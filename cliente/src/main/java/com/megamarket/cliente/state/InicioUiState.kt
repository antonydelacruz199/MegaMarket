package com.megamarket.cliente.state

import com.megamarket.modelo.Producto

data class InicioUiState(
    val cargando: Boolean = true,
    val ofertas: List<Producto> = emptyList(),
    val hayProductos: Boolean = false,
    val error: String? = null
)
