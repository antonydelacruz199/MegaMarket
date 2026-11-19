package com.megamarket.picker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElegibilidadPickingTest {

    @Test
    fun recibidoSiemprePuedeTomarse() {
        assertTrue(puedePasarAEnPicking("RECIBIDO", null))
        assertTrue(puedePasarAEnPicking("RECIBIDO", PagoDtoConsulta("ONLINE")))
        assertTrue(puedePasarAEnPicking("RECIBIDO", PagoDtoConsulta("CONTRAENTREGA")))
    }

    @Test
    fun confirmadoSinPagoOContraentregaPuedeTomarse() {
        assertTrue(puedePasarAEnPicking("CONFIRMADO", null))
        assertTrue(puedePasarAEnPicking("CONFIRMADO", PagoDtoConsulta("CONTRAENTREGA")))
        assertTrue(puedePasarAEnPicking("CONFIRMADO", PagoDtoConsulta("contraentrega")))
    }

    @Test
    fun onlineConfirmadoPendienteNoPuedeTomarseComoLegado() {
        assertFalse(puedePasarAEnPicking("CONFIRMADO", PagoDtoConsulta("ONLINE")))
        assertFalse(puedePasarAEnPicking("CONFIRMADO", PagoDtoConsulta(null)))
    }

    @Test
    fun pagadoSiPuedeTomarse() {
        assertTrue(puedePasarAEnPicking("PAGADO", PagoDtoConsulta("ONLINE")))
        assertTrue(puedePasarAEnPicking("PAGADO", null))
    }

    @Test
    fun otrosEstadosNoSeOfrecen() {
        assertFalse(puedePasarAEnPicking("EN_PICKING", null))
        assertFalse(puedePasarAEnPicking("LISTO", null))
        assertFalse(puedePasarAEnPicking("EN_CAMINO", null))
    }
}
