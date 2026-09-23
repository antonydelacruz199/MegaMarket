package com.megamarket.app.estado

import com.megamarket.app.modelo.Producto

data class EstadoUiCatalogo(
    val cargando: Boolean = false,
    val productos: List<Producto> = emptyList(),
    val consulta: String = "",
    val error: String? = null
)
