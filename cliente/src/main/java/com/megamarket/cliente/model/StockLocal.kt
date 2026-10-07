package com.megamarket.cliente.model

/**
 * Reglas locales de stock (sin Android). El descuento real ocurre en el DAO del admin
 * con UPDATE … WHERE stock >= :cantidad; aquí se documenta el resultado esperado.
 */
object StockLocal {
    /** null = no se puede descontar (evita stock negativo). */
    fun stockTrasDescuento(stockActual: Int, cantidad: Int): Int? {
        if (cantidad <= 0) return null
        if (stockActual < cantidad) return null
        return stockActual - cantidad
    }

    /**
     * Consolida actualizaciones sucesivas de stock pendiente: gana el valor final más reciente.
     */
    fun consolidarStockPendiente(
        pendientes: Map<Long, Int>,
        productoId: Long,
        stockFinal: Int
    ): Map<Long, Int> = pendientes + (productoId to stockFinal)
}
