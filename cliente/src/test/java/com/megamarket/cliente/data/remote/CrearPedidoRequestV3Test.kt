package com.megamarket.cliente.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrearPedidoRequestV3Test {

    private val gson = Gson()

    @Test
    fun serializaContratoV3ConFranjaYModalidad() {
        val json = gson.toJson(
            CrearPedidoRequest(
                clientUuid = "cart-1",
                contratoVersion = 3,
                direccion = DireccionPedidoRequest(
                    departamento = "Junín",
                    provincia = "Huancayo",
                    distrito = "El Tambo",
                    direccion = "Calle 1",
                    telefono = "999999999"
                ),
                items = listOf(ItemPedidoRequest("prod-1", 2, 100)),
                franjaEntregaId = "11111111-2222-3333-4444-555555555555",
                modalidadPago = "ONLINE"
            )
        )
        assertTrue(json.contains("\"contratoVersion\":3"))
        assertTrue(json.contains("\"franjaEntregaId\""))
        assertTrue(json.contains("\"modalidadPago\":\"ONLINE\""))
        assertFalse(json.contains("\"contratoVersion\":2"))
    }

    @Test
    fun v2OmiteCamposNulosEnGsonPorDefecto() {
        val request = CrearPedidoRequest(
            clientUuid = "cart-2",
            contratoVersion = 2,
            direccion = DireccionPedidoRequest("a", "b", "c", "d", "1"),
            items = emptyList()
        )
        assertEquals(2, request.contratoVersion)
        assertEquals(null, request.franjaEntregaId)
        assertEquals(null, request.modalidadPago)
    }
}
