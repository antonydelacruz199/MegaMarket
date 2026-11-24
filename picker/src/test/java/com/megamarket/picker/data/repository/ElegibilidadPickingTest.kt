package com.megamarket.picker.data.repository

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
    fun pagadoYaNoSeTomaAPickingSeDespacha() {
        assertFalse(puedePasarAEnPicking("PAGADO", PagoDtoConsulta("ONLINE")))
        assertFalse(puedePasarAEnPicking("PAGADO", null))
        assertTrue(puedeMandarADelivery("PAGADO", PagoDtoConsulta("ONLINE")))
        assertTrue(puedeMandarADelivery("PAGADO", null))
    }

    @Test
    fun confirmadoContraentregaPuedeDespacharse() {
        assertTrue(puedeMandarADelivery("CONFIRMADO", null))
        assertTrue(puedeMandarADelivery("CONFIRMADO", PagoDtoConsulta("CONTRAENTREGA")))
        assertFalse(puedeMandarADelivery("CONFIRMADO", PagoDtoConsulta("ONLINE")))
        assertFalse(puedeMandarADelivery("RECIBIDO", null))
    }

    @Test
    fun otrosEstadosNoSeOfrecen() {
        assertFalse(puedePasarAEnPicking("EN_PICKING", null))
        assertFalse(puedePasarAEnPicking("LISTO", null))
        assertFalse(puedePasarAEnPicking("EN_CAMINO", null))
    }
}
