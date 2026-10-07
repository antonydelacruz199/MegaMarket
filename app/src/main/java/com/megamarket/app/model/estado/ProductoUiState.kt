package com.megamarket.app.model.estado

import com.megamarket.app.model.FiltroProducto
import com.megamarket.modelo.Producto

data class ProductoUiState(
    val cargando: Boolean = false,
    val productos: List<Producto> = emptyList(),
    val consulta: String = "",
    val filtro: FiltroProducto = FiltroProducto.TODOS,
    val error: String? = null
) {
    val totalProductos: Int
        get() = productos.size

    val ofertasActivas: Int
        get() = productos.count { it.ofertaValida }

    val stockBajo: Int
        get() = productosStockBajo.size

    val productosStockBajo: List<Producto>
        get() = productos.filter { it.stockBajo }.sortedBy { it.stock }

    val agotados: Int
        get() = productos.count { it.agotado }

    val visibles: List<Producto>
        get() {
            val texto = consulta.trim()
            return productos.filter { producto ->
                val coincideTexto = texto.isEmpty() ||
                    producto.nombre.contains(texto, ignoreCase = true) ||
                    producto.marca.contains(texto, ignoreCase = true)
                coincideTexto && filtro.admite(producto)
            }
        }
}
