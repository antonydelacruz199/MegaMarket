package com.megamarket.cliente.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ImporteClienteSustitucionTest {

    @Test
    fun nuncaPagaMasQueElOriginalCuandoSustitutoEsMasCaro() {
        assertEquals(2000L, importeClienteCentimos(1000, 1200, 2))
    }

    @Test
    fun pagaElPrecioDelSustitutoCuandoEsMasBarato() {
        assertEquals(1600L, importeClienteCentimos(1000, 800, 2))
    }

    @Test
    fun cantidadCeroDaCero() {
        assertEquals(0L, importeClienteCentimos(1000, 900, 0))
    }
}
