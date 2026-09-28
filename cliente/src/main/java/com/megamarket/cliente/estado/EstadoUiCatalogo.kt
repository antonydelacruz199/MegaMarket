package com.megamarket.cliente.estado

import com.megamarket.modelo.Producto

data class EstadoUiCatalogo(
    val cargando: Boolean = true,
    val productos: List<Producto> = emptyList(),
    val consulta: String = "",
    val categoriaId: Long? = null,
    val soloOfertas: Boolean = false,
    val error: String? = null
) {
    val categorias: List<Long>
        get() = productos.map { it.categoriaId }.distinct().sorted()

    val visibles: List<Producto>
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

    val vacio: Boolean
        get() = !cargando && error == null && productos.isEmpty()

    val sinResultados: Boolean
        get() = !cargando && error == null && productos.isNotEmpty() && visibles.isEmpty()
}
