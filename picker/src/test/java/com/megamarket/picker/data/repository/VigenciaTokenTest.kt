package com.megamarket.picker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VigenciaTokenTest {

    @Test
    fun interpretaLaFechaIsoDelServidor() {
        val millis = epochMillisIso("2026-11-16T01:03:00.000Z")
        assertEquals(epochMillisIso("2026-11-16T01:03:00Z"), millis)
        assertTrue(millis != null && millis > 0L)
    }

    @Test
    fun tokenVigenteAntesDelVencimiento() {
        val fin = epochMillisIso("2099-01-01T00:00:00.000Z")!!
        assertTrue(tokenSigueVigente("2099-01-01T00:00:00.000Z", fin - 1))
        assertFalse(tokenSigueVigente("2099-01-01T00:00:00.000Z", fin))
    }

    @Test
    fun fechaAusenteOIlegibleNoCaducaEnLocal() {
        assertTrue(tokenSigueVigente(null, 0L))
        assertTrue(tokenSigueVigente("manana", 0L))
        assertNull(epochMillisIso("   "))
    }
}
