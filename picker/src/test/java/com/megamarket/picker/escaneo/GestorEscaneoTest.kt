package com.megamarket.picker.escaneo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestorEscaneoTest {

    @Test
    fun fotogramasConsecutivosNoRepitenLaSolicitud() {
        var creados = 0
        val gestor = GestorEscaneo { creados += 1; "uuid-$creados" }
        val primera = gestor.frame("770001")
        assertEquals("770001", primera?.codigoBarras)
        assertEquals(1, primera?.cantidad)
        assertNull(gestor.frame("770001"))
        assertNull(gestor.frame("770001"))
        assertEquals(1, creados)
    }

    @Test
    fun reintentoReutilizaElMismoUuid() {
        var creados = 0
        val gestor = GestorEscaneo { creados += 1; "uuid-$creados" }
        val primera = gestor.frame("770001")
        gestor.falloRecuperable()
        assertNull(gestor.frame("770001"))
        val reintento = gestor.reintentar()
        assertEquals(primera?.uuidOperacion, reintento?.uuidOperacion)
        assertEquals("770001", reintento?.codigoBarras)
        assertEquals(1, reintento?.cantidad)
        assertEquals(1, creados)
    }

    @Test
    fun manualConservaCerosIniciales() {
        val gestor = GestorEscaneo { "uuid-1" }
        val solicitud = gestor.manual("  00770001")
        assertEquals("00770001", solicitud?.codigoBarras)
        assertEquals(1, solicitud?.cantidad)
        assertEquals("00770001", textoCodigoBarras("00770001"))
    }

    @Test
    fun despuesDelResultadoEsperaUnHuecoAntesDeRepetirElCodigo() {
        var creados = 0
        val gestor = GestorEscaneo { creados += 1; "uuid-$creados" }
        gestor.frame("770001")
        gestor.resultadoListo()
        assertNull(gestor.frame("770001"))
        assertNull(gestor.frame("888888"))
        gestor.continuar()
        assertNull(gestor.frame("770001"))
        gestor.frame(null)
        assertEquals("uuid-2", gestor.frame("770001")?.uuidOperacion)
    }

    @Test
    fun manualDelMismoCodigoTrasUnFalloReutilizaElUuid() {
        val gestor = GestorEscaneo { "uuid-1" }
        gestor.manual("00770001")
        gestor.falloRecuperable()
        val reintento = gestor.manual("00770001")
        assertEquals("uuid-1", reintento?.uuidOperacion)
        assertEquals("00770001", reintento?.codigoBarras)
    }
}
