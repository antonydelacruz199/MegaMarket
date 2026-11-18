package com.megamarket.cliente.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FechaLimiteTest {

    @Test
    fun cuentaRegresivaDesdeFechaServidor() {
        val limite = "2030-01-01T00:00:05.000Z"
        val ahora = parsearIsoAMillis("2030-01-01T00:00:00.000Z")!!
        val restante = milisRestantesHasta(limite, ahora)
        assertEquals(5_000L, restante)
        assertEquals("0:05", formatearTiempoRestante(restante))
    }

    @Test
    fun vencidaDevuelveCeroSinResolverLocalmente() {
        val restante = milisRestantesHasta("2020-01-01T00:00:00.000Z", System.currentTimeMillis())
        assertEquals(0L, restante)
        assertEquals("0:00", formatearTiempoRestante(restante))
    }

    @Test
    fun fechaInvalidaNoRevienta() {
        assertEquals(0L, milisRestantesHasta("no-es-fecha"))
        assertTrue(parsearIsoAMillis(null) == null)
    }
}
