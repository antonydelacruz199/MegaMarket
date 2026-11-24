package com.megamarket.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoFechaTest {

    @Test
    fun normalizaIsoYDdMmYyyy() {
        assertEquals("2099-06-15", normalizarFechaIso("2099-06-15"))
        assertEquals("2099-06-15", normalizarFechaIso("15/06/2099"))
        assertEquals("2099-06-15", normalizarFechaIso("2099-06-15T00:00:00.000Z"))
    }

    @Test
    fun presentaSoloFechaComoDdMmYyyy() {
        assertEquals("15/06/2099", formatearFechaPresentacion("2099-06-15"))
        assertEquals("15/06/2099", formatearFechaPresentacion("15/06/2099"))
    }

    @Test
    fun presentaFechaHoraEnZonaPeru() {
        val texto = formatearFechaHoraPresentacion("2030-06-15T12:00:00.000Z")
        assertTrue(texto.contains("15/06/2030"))
        assertEquals("—", formatearFechaHoraPresentacion(null))
        assertEquals("no-es-fecha", formatearFechaHoraPresentacion("no-es-fecha"))
    }
}
