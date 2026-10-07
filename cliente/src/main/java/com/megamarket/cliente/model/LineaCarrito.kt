package com.megamarket.cliente.model

import com.megamarket.modelo.Producto

data class LineaCarrito(
    val producto: Producto,
    val cantidad: Int
) {
    val subtotalCentimos: Long
        get() = if (producto.agotado) 0L else producto.precioVigenteCentimos * cantidad

    /** El stock bajó después de agregar el producto; hay que reducir la cantidad para comprar. */
    val excedeStock: Boolean
        get() = !producto.agotado && cantidad > producto.stock

    val comprable: Boolean
        get() = producto.activo && !producto.agotado && cantidad in 1..producto.stock
}

fun List<LineaCarrito>.totalCentimos(): Long = sumOf { it.subtotalCentimos }
