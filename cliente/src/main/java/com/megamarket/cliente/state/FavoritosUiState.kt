package com.megamarket.cliente.state

import com.megamarket.modelo.Producto

data class FavoritosUiState(
    val cargando: Boolean = true,
    val productos: List<Producto> = emptyList(),
    val limiteVisible: Int = TAMANO_PAGINA_LISTA,
    val error: String? = null
) {
    val visibles: List<Producto>
        get() = productos.take(limiteVisible)

    val hayMas: Boolean
        get() = productos.size > limiteVisible
}
