package com.megamarket.cliente.estado

import com.megamarket.modelo.Producto

data class EstadoUiInicio(
    val cargando: Boolean = true,
    val ofertas: List<Producto> = emptyList(),
    val hayProductos: Boolean = false,
    val error: String? = null
)
