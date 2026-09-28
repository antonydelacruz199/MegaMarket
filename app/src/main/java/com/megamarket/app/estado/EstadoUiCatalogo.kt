package com.megamarket.app.estado

import com.megamarket.modelo.Producto

data class EstadoUiCatalogo(
    val cargando: Boolean = false,
    val productos: List<Producto> = emptyList(),
    val consulta: String = "",
    val error: String? = null
) {
    val totalProductos: Int
        get() = productos.size

    val ofertasActivas: Int
        get() = productos.count { it.ofertaValida }

    val stockBajo: Int
        get() = productos.count { it.stock in 1..5 }

    val agotados: Int
        get() = productos.count { it.agotado }
}
