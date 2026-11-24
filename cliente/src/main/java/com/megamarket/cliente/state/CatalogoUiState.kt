package com.megamarket.cliente.state

import com.megamarket.modelo.Categoria
import com.megamarket.modelo.Producto

const val TAMANO_PAGINA_LISTA = 20

data class CatalogoUiState(
    val cargando: Boolean = true,
    val productos: List<Producto> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val consulta: String = "",
    val categoriaId: Long? = null,
    val soloOfertas: Boolean = false,
    val limiteVisible: Int = TAMANO_PAGINA_LISTA,
    val error: String? = null
) {
    val filtrados: List<Producto>
        get() {
            val texto = consulta.trim()
            return productos.filter { producto ->
                val coincideTexto = texto.isEmpty() ||
                    producto.nombre.contains(texto, ignoreCase = true) ||
                    producto.marca.contains(texto, ignoreCase = true)
                val coincideCategoria = categoriaId == null || producto.categoriaId == categoriaId
                val coincideOferta = !soloOfertas || producto.ofertaValida
                coincideTexto && coincideCategoria && coincideOferta
            }
        }

    val visibles: List<Producto>
        get() = filtrados.take(limiteVisible)

    val hayMas: Boolean
        get() = filtrados.size > limiteVisible

    val vacio: Boolean
        get() = !cargando && error == null && productos.isEmpty()

    val sinResultados: Boolean
        get() = !cargando && error == null && productos.isNotEmpty() && filtrados.isEmpty()
}
