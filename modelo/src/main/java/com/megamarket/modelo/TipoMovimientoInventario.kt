package com.megamarket.modelo

/** Tipos de movimiento de inventario (delta). Nunca stock absoluto. */
object TipoMovimientoInventario {
    const val SALIDA_VENTA = "SALIDA_VENTA"
    const val AJUSTE_ENTRADA = "AJUSTE_ENTRADA"
    const val AJUSTE_SALIDA = "AJUSTE_SALIDA"
}
