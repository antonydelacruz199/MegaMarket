package com.megamarket.app.model

import com.megamarket.modelo.Producto

enum class FiltroProducto(val etiqueta: String) {
    TODOS("Todos"),
    ACTIVOS("Activos"),
    OFERTAS("Ofertas"),
    STOCK_BAJO("Stock bajo");

    fun admite(producto: Producto): Boolean = when (this) {
        TODOS -> true
        ACTIVOS -> producto.activo
        OFERTAS -> producto.esOferta
        STOCK_BAJO -> producto.stockBajo
    }
}
