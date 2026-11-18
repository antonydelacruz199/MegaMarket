package com.megamarket.picker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FechaLimiteTest {

    @Test
    fun parseaIsoConMilisegundosUtc() {
        val millis = parsearIsoAMillis("2026-11-16T17:05:00.000Z")
        assertNotNull(millis)
        assertTrue(millis!! > 0L)
    }

    @Test
    fun milisRestantesNuncaNegativos() {
        val limite = "2020-01-01T00:00:00.000Z"
        assertEquals(0L, milisRestantesHasta(limite, ahoraMillis = System.currentTimeMillis()))
    }

    @Test
    fun formateaMinutosYSegundos() {
        assertEquals("1:05", formatearTiempoRestante(65_000L))
        assertEquals("0:00", formatearTiempoRestante(0L))
        assertEquals("0:09", formatearTiempoRestante(9_500L))
    }

    @Test
    fun calculaRestanteRespectoAlServidor() {
        val limite = parsearIsoAMillis("2026-11-16T17:05:00.000Z")!!
        val ahora = limite - 125_000L
        assertEquals(125_000L, milisRestantesHasta("2026-11-16T17:05:00.000Z", ahora))
        assertEquals("2:05", formatearTiempoRestante(125_000L))
    }
}
