package com.megamarket.app.data.repository

import org.json.JSONObject

internal object ProductoSyncPayload {
    fun referenciaProducto(productoIdLocal: Long): String =
        JSONObject().put("productoIdLocal", productoIdLocal).toString()

    fun movimiento(productoIdLocal: Long, tipo: String, cantidad: Int): String =
        JSONObject()
            .put("productoIdLocal", productoIdLocal)
            .put("tipo", tipo)
            .put("cantidad", cantidad)
            .toString()
}

/** Delta de stock para movimientos admin (testable sin Room). */
object ProductoInventarioLocal {
    data class Ajuste(val tipo: String, val cantidad: Int)

    fun calcularAjuste(stockAnterior: Int, stockNuevo: Int): Ajuste? {
        if (stockNuevo < 0) return null
        val delta = stockNuevo - stockAnterior
        return when {
            delta > 0 -> Ajuste(
                tipo = com.megamarket.modelo.TipoMovimientoInventario.AJUSTE_ENTRADA,
                cantidad = delta
            )
            delta < 0 -> Ajuste(
                tipo = com.megamarket.modelo.TipoMovimientoInventario.AJUSTE_SALIDA,
                cantidad = -delta
            )
            else -> null
        }
    }
}
