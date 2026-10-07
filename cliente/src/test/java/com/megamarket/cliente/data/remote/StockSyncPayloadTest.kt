package com.megamarket.cliente.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class StockSyncPayloadTest {

    @Test
    fun operacionGuardaValorFinal_noDelta() {
        val json = StockSyncPayload(productoIdLocal = 15, stock = 7).aJson()
        assertEquals("""{"productoIdLocal":15,"stock":7}""", json)
        val leido = StockSyncPayload.desdeJson(json)
        assertEquals(15L, leido.productoIdLocal)
        assertEquals(7, leido.stock)
    }

    @Test
    fun reintentoConMismoPayload_sigueSiendoSiete() {
        val primero = StockSyncPayload(15, 7).aJson()
        val segundo = StockSyncPayload.desdeJson(primero).aJson()
        assertEquals(primero, segundo)
        assertEquals(7, StockSyncPayload.desdeJson(segundo).stock)
    }
}
