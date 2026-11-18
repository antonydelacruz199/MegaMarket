package com.megamarket.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UbicacionProductoTest {

    @Test
    fun vacio_equivaleAProductoAntiguo() {
        assertNull((UbicacionProducto.normalizarCodigo("  ") as UbicacionProducto.Resultado.Ok).valor)
        assertNull((UbicacionProducto.normalizarPasillo(null) as UbicacionProducto.Resultado.Ok).valor)
    }

    @Test
    fun rechazaLongitudYCaracteres() {
        val largo = "1".repeat(UbicacionProducto.CODIGO_MAX + 1)
        assertTrue(UbicacionProducto.normalizarCodigo(largo) is UbicacionProducto.Resultado.Error)
        assertTrue(UbicacionProducto.normalizarPasillo("x".repeat(41)) is UbicacionProducto.Resultado.Error)
        assertEquals("A-1", (UbicacionProducto.normalizarPasillo("A-1") as UbicacionProducto.Resultado.Ok).valor)
    }
}
