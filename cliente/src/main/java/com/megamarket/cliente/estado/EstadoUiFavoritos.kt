package com.megamarket.cliente.estado

import com.megamarket.modelo.Producto

data class EstadoUiFavoritos(
    val cargando: Boolean = true,
    val productos: List<Producto> = emptyList(),
    val error: String? = null
)
