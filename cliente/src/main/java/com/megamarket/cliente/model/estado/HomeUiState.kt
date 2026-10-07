package com.megamarket.cliente.model.estado

import com.megamarket.modelo.Producto

data class HomeUiState(
    val cargando: Boolean = true,
    val ofertas: List<Producto> = emptyList(),
    val hayProductos: Boolean = false,
    val error: String? = null
)
