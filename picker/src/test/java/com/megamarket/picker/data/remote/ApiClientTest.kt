package com.megamarket.picker.data.remote

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress

class ApiClientTest {

    @Test
    fun loginNoEnviaTokenYMeUsaElDelUsuarioActivo() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var autorizacionLogin: String? = "no-llamado"
        var autorizacionMe: String? = null
        var expectedMe: String? = "presente"
        server.createContext("/api/auth/login") { exchange ->
            autorizacionLogin = exchange.requestHeaders.getFirst("Authorization")
            exchange.requestBody.close()
            val bytes = """
                {"accessToken":"token-login","tokenType":"Bearer","expiresAt":"2099-01-01T00:00:00.000Z",
                "usuario":{"id":"ana-id","nombre":"Ana","usuario":"ana","correo":"a@b.c","rol":"PICKER","version":1}}
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/auth/me") { exchange ->
            autorizacionMe = exchange.requestHeaders.getFirst("Authorization")
            expectedMe = exchange.requestHeaders.getFirst("X-Expected-User")
            exchange.requestBody.close()
            val bytes = """
                {"usuario":{"id":"ana-id","nombre":"Ana","usuario":"ana","correo":"a@b.c","rol":"PICKER","version":1}}
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = ApiClient(
                baseUrl = "http://127.0.0.1:${server.address.port}/",
                tokenProvider = { "token-otro" },
                ownerProvider = { "ana-id" },
                tokenForOwner = { if (it == "ana-id") "token-ana" else null }
            ).crear()
            assertTrue(
                api.login(LoginRequest(usuario = "ana", clave = "secreta", deviceName = "Prueba")).isSuccessful
            )
            assertNull(autorizacionLogin)
            assertTrue(api.me("ana-id").isSuccessful)
            assertEquals("Bearer token-ana", autorizacionMe)
            assertNull(expectedMe)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun cambiarDeUsuarioDetieneLaSolicitudAntesDeEnviarla() = runBlocking {
        for (ownerActivo in listOf<String?>(null, "otro-id")) {
            val api = ApiClient(
                baseUrl = "http://127.0.0.1:1/",
                tokenProvider = { "token-otro" },
                ownerProvider = { ownerActivo },
                tokenForOwner = { "token-ana" }
            ).crear()
            try {
                api.me("ana-id")
                fail("No debe enviar /me de Ana con otra sesión")
            } catch (error: IOException) {
                assertEquals(ApiClient.MENSAJE_CUENTA_CAMBIADA, error.message)
            }
        }
    }

    @Test
    fun laFotoDeProductoViajaConJwt() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var autorizacion: String? = null
        var accept: String? = null
        server.createContext("/api/medios/productos/prod-1") { exchange ->
            autorizacion = exchange.requestHeaders.getFirst("Authorization")
            accept = exchange.requestHeaders.getFirst("Accept")
            exchange.requestBody.close()
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val cliente = ApiClient(
                baseUrl = "http://127.0.0.1:${server.address.port}/",
                tokenProvider = { "token-picker" },
                ownerProvider = { "picker-1" },
                tokenForOwner = { "token-picker" }
            ).http
            val respuesta = cliente.newCall(
                okhttp3.Request.Builder()
                    .url("http://127.0.0.1:${server.address.port}/api/medios/productos/prod-1?download=1")
                    .build()
            ).execute()
            assertEquals(200, respuesta.code)
            assertEquals("Bearer token-picker", autorizacion)
            assertTrue(accept!!.contains("image/jpeg"))
            respuesta.close()
        } finally {
            server.stop(0)
        }
    }
}
