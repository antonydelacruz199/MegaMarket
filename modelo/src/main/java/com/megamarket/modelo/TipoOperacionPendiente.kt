package com.megamarket.modelo

object TipoOperacionPendiente {
    const val PRODUCTO = "PRODUCTO"
    const val PEDIDO = "PEDIDO"
    const val MOVIMIENTO_INVENTARIO = "MOVIMIENTO_INVENTARIO"

    const val CREAR_PRODUCTO = "CREAR_PRODUCTO"
    const val ACTUALIZAR_PRODUCTO = "ACTUALIZAR_PRODUCTO"
    const val ELIMINAR_PRODUCTO = "ELIMINAR_PRODUCTO"
    const val CREAR_PEDIDO = "CREAR_PEDIDO"
    const val CREAR_MOVIMIENTO_INVENTARIO = "CREAR_MOVIMIENTO_INVENTARIO"

    /** Legacy cliente: stock absoluto. No enviar al backend. */
    const val ACTUALIZAR_STOCK = "ACTUALIZAR_STOCK"
}
