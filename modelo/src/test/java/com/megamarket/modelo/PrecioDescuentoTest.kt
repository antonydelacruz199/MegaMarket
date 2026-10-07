package com.megamarket.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecioDescuentoTest {

    @Test
    fun descuento20_sobre1000_es800() {
        assertEquals(800L, PrecioDescuento.precioConDescuentoCentimos(1000, 20))
    }

    @Test
    fun descuento0_mantienePrecio() {
        assertEquals(1500L, PrecioDescuento.precioConDescuentoCentimos(1500, 0))
    }

    @Test
    fun redondeoEnteroHaciaAbajo() {
        // 999 * 33 / 100 = 329.67 → 329
        assertEquals(329L, PrecioDescuento.precioConDescuentoCentimos(999, 67))
    }

    @Test
    fun subtotalLinea_rf09() {
        val unitario = PrecioDescuento.precioConDescuentoCentimos(1000, 20)
        assertEquals(2400L, unitario * 3)
    }

    @Test
    fun derivaPorcentajeDesdePrecioOferta() {
        assertEquals(20, PrecioDescuento.descuentoDesdePrecioOferta(1000, 800))
        assertEquals(0, PrecioDescuento.descuentoDesdePrecioOferta(1000, 1000))
    }

    @Test
    fun ofertaActivaRequiereRango() {
        assertTrue(PrecioDescuento.esOfertaActiva(true, 20))
        assertFalse(PrecioDescuento.esOfertaActiva(true, 0))
        assertFalse(PrecioDescuento.esOfertaActiva(false, 20))
        assertFalse(PrecioDescuento.esOfertaActiva(true, 100))
    }
}
