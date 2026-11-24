package com.megamarket.app.state

import com.megamarket.app.model.FiltroProducto
import com.megamarket.modelo.Producto

const val TAMANO_PAGINA_ADMIN = 20

data class ProductoUiState(
    val cargando: Boolean = false,
    val productos: List<Producto> = emptyList(),
    val consulta: String = "",
    val filtro: FiltroProducto = FiltroProducto.TODOS,
    val limiteVisible: Int = TAMANO_PAGINA_ADMIN,
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

    val filtrados: List<Producto>
        get() {
            val texto = consulta.trim()
            return productos.filter { producto ->
                val coincideTexto = texto.isEmpty() ||
                    producto.nombre.contains(texto, ignoreCase = true) ||
                    producto.marca.contains(texto, ignoreCase = true)
                coincideTexto && filtro.admite(producto)
            }
        }

    val visibles: List<Producto>
        get() = filtrados.take(limiteVisible)

    val hayMas: Boolean
        get() = filtrados.size > limiteVisible
}
