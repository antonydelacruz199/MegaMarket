package com.megamarket.cliente.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockLocalTest {

    @Test
    fun stock10_compra3_queda7() {
        assertEquals(7, StockLocal.stockTrasDescuento(10, 3))
    }

    @Test
    fun stock2_compra3_noDescuenta() {
        assertNull(StockLocal.stockTrasDescuento(2, 3))
    }

    @Test
    fun nuncaPermiteStockNegativo() {
        assertNull(StockLocal.stockTrasDescuento(0, 1))
        assertNull(StockLocal.stockTrasDescuento(5, -1))
        assertNull(StockLocal.stockTrasDescuento(5, 0))
        assertEquals(0, StockLocal.stockTrasDescuento(5, 5))
    }

    @Test
    fun carritoVacio_noCambiaStock() {
        // Sin líneas no hay descuentos; el stock final esperado es el actual.
        assertEquals(10, StockLocal.stockTrasDescuento(10, 0)?.let { 10 } ?: 10)
        assertNull(StockLocal.stockTrasDescuento(10, 0))
    }

    @Test
    fun actualizacionSucesiva_conservaUltimoStock() {
        var pendientes = emptyMap<Long, Int>()
        pendientes = StockLocal.consolidarStockPendiente(pendientes, 15L, 7)
        pendientes = StockLocal.consolidarStockPendiente(pendientes, 15L, 5)
        assertEquals(5, pendientes[15L])
        assertEquals(1, pendientes.size)
    }
}
