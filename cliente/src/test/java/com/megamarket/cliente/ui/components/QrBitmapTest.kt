package com.megamarket.cliente.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrBitmapTest {

    @Test
    fun codificaContenidoSimuladoEnUnaMatrizCuadradaConModulosOscuros() {
        val matriz = QrBitmap.crearMatriz("MEGAMARKET-SIM|pago-1|SIM-REF-ABC", 256)
        assertNotNull(matriz)
        assertEquals(256, matriz!!.width)
        assertEquals(256, matriz.height)
        var oscuros = 0
        for (y in 0 until matriz.height) for (x in 0 until matriz.width) if (matriz[x, y]) oscuros++
        assertTrue(oscuros > 0)
    }

    @Test
    fun contenidoVacioONegativoNoGeneraQr() {
        assertNull(QrBitmap.crearMatriz("   ", 128))
        assertNull(QrBitmap.crearMatriz("abc", 0))
    }
}
