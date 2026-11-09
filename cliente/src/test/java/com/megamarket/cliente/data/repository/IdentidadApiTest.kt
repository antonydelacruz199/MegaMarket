package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.ApiClient
import com.megamarket.cliente.data.remote.ReservaRequest
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IdentidadApiTest {
    @Test fun unaSolicitudUsaElTokenDeSuPropietario() = runBlocking {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var authorization: String?=null
        var expectedHeader: String?=null
        server.createContext("/api/carritos/cart-ana") { exchange ->
            authorization=exchange.requestHeaders.getFirst("Authorization")
            expectedHeader=exchange.requestHeaders.getFirst("X-Expected-User")
            exchange.requestBody.close()
            val bytes="{\"revision\":1}".toByteArray()
            exchange.sendResponseHeaders(200,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api=ApiClient("http://127.0.0.1:${server.address.port}/",{ "token-pedro" },{ "ana" },{ if(it=="ana") "token-ana" else null }).crear()
            assertTrue(api.reservarCarrito("cart-ana",ReservaRequest(1,emptyList()),"ana").isSuccessful)
            assertEquals("Bearer token-ana",authorization)
            assertNull(expectedHeader)
        } finally { server.stop(0) }
    }

    @Test fun cerrarOCambiarSesionDetieneLaSolicitudAntesDeEnviarla() = runBlocking {
        for(ownerActivo in listOf<String?>(null,"pedro")) {
            // No hay servidor: debe fallar en el interceptor por identidad, antes de conectar.
            val api=ApiClient("http://127.0.0.1:1/",{ "token-pedro" },{ ownerActivo },{ "token-ana" }).crear()
            try {
                api.reservarCarrito("cart-ana",ReservaRequest(1,emptyList()),"ana")
                fail("No debe enviar un carrito de Ana con otra sesión")
            } catch(error: IOException) {
                assertEquals("La cuenta activa cambió",error.message)
            }
        }
    }
}
