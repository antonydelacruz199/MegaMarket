package com.megamarket.cliente.modelo

import com.megamarket.modelo.Producto

data class LineaCarrito(
    val producto: Producto,
    val cantidad: Int
) {
    val subtotalCentimos: Long
        get() = if (producto.agotado) 0L else producto.precioVigenteCentimos * cantidad
}
