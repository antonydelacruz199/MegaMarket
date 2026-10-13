package com.megamarket.cliente.state

import com.megamarket.modelo.Producto

data class FavoritosUiState(
    val cargando: Boolean = true,
    val productos: List<Producto> = emptyList(),
    val error: String? = null
)
