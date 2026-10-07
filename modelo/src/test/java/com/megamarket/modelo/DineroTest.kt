package com.megamarket.modelo

import org.junit.Assert.assertEquals
import org.junit.Test

class DineroTest {

    @Test
    fun formatearSoles_muestraDosDecimales() {
        assertEquals("S/ 0.00", 0L.formatearSoles())
        assertEquals("S/ 0.05", 5L.formatearSoles())
        assertEquals("S/ 4.80", 480L.formatearSoles())
        assertEquals("S/ 1250.99", 125_099L.formatearSoles())
    }

    @Test
    fun formatearSoles_conservaElSigno() {
        assertEquals("-S/ 3.50", (-350L).formatearSoles())
    }
}
