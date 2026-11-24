package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.ConfirmarPagoRequest
import com.megamarket.cliente.data.remote.GenerarQrRequest
import com.megamarket.cliente.data.remote.CategoriasResponse
import com.megamarket.cliente.data.remote.CrearPedidoRequest
import com.megamarket.cliente.data.remote.CrearPedidoResponse
import com.megamarket.cliente.data.remote.FranjaApiDto
import com.megamarket.cliente.data.remote.FranjaResponse
import com.megamarket.cliente.data.remote.ListaFranjasResponse
import com.megamarket.cliente.data.remote.LoginRequest
import com.megamarket.cliente.data.remote.LoginResponse
import com.megamarket.cliente.data.remote.ProductosResponse
import com.megamarket.cliente.data.remote.ReservaRequest
import com.megamarket.cliente.data.remote.ReservaResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class FranjasRepositoryTest {

    @Test
    fun listarDisponiblesYAgotadas() = runBlocking {
        val api = ApiFranjas(
            listOf(
                FranjaApiDto(
                    id = "ok",
                    fecha = "2099-06-15",
                    horaInicio = "09:00:00",
                    horaFin = "11:00:00",
                    capacidad = 20,
                    ocupados = 1,
                    cupoRestante = 19,
                    activa = true
                ),
                FranjaApiDto(
                    id = "full",
                    fecha = "2099-06-15",
                    horaInicio = "11:00:00",
                    horaFin = "13:00:00",
                    capacidad = 1,
                    ocupados = 1,
                    cupoRestante = 0,
                    activa = true
                )
            )
        )
        val repo = FranjasRepository(api, usuarioRemotoId = { "u1" })
        val resultado = repo.listar() as ResultadoFranjas.Exito
        assertEquals(2, resultado.valor.size)
        assertTrue(resultado.valor.first { it.id == "ok" }.disponible)
        assertTrue(!resultado.valor.first { it.id == "full" }.disponible)
        assertTrue(resultado.valor.none { it.etiquetaCompleta.contains("ok") && it.etiquetaCompleta.length == 2 })
    }

    @Test
    fun errorDeRedSePropaga() = runBlocking {
        val repo = FranjasRepository(ApiFranjas(fallarRed = true), usuarioRemotoId = { "u1" })
        val resultado = repo.listar()
        assertTrue(resultado is ResultadoFranjas.Error)
    }

    @Test
    fun conflictoHttpDevuelveMensaje() = runBlocking {
        val repo = FranjasRepository(
            api = ApiFranjas(
                errorHttp = Response.error(
                    409,
                    """{"error":"La franja no tiene cupo disponible","codigo":"FRANJA_LLENA"}"""
                        .toResponseBody("application/json".toMediaType())
                )
            ),
            usuarioRemotoId = { "u1" }
        )
        val resultado = repo.obtener("x") as ResultadoFranjas.Error
        assertEquals("FRANJA_LLENA", resultado.codigo)
        assertTrue(resultado.mensaje.contains("cupo"))
    }

    private class ApiFranjas(
        private val franjas: List<FranjaApiDto> = emptyList(),
        private val fallarRed: Boolean = false,
        private val errorHttp: Response<ListaFranjasResponse>? = null
    ) : ApiService {
        override suspend fun reservarCarrito(id: String, body: ReservaRequest, owner: String): Response<ReservaResponse> =
            error("n/a")
        override suspend fun login(body: LoginRequest): Response<LoginResponse> = error("n/a")
        override suspend fun listarCategorias(owner: String?): Response<CategoriasResponse> = error("n/a")
        override suspend fun listarProductos(owner: String?, page: Int?, limit: Int?, q: String?): Response<ProductosResponse> = error("n/a")
        override suspend fun listarFranjas(owner: String?): Response<ListaFranjasResponse> {
            if (fallarRed) throw IOException("sin red")
            errorHttp?.let { return it as Response<ListaFranjasResponse> }
            return Response.success(ListaFranjasResponse(franjas = franjas))
        }
        override suspend fun obtenerFranja(id: String, owner: String?): Response<FranjaResponse> {
            if (fallarRed) throw IOException("sin red")
            if (errorHttp != null) {
                return Response.error(
                    errorHttp.code(),
                    errorHttp.errorBody()!!
                )
            }
            return Response.success(FranjaResponse(franja = franjas.firstOrNull { it.id == id }))
        }
        override suspend fun crearPedido(body: CrearPedidoRequest, owner: String?): Response<CrearPedidoResponse> =
            error("n/a")
        override suspend fun listarPedidos(
            estado: String?,
            owner: String,
            page: Int?,
            limit: Int?,
            q: String?,
            filtro: String?,
            estados: String?
        ) = error("n/a")
        override suspend fun obtenerPedido(id: String, owner: String) = error("n/a")
        override suspend fun transicionarPedido(
            id: String,
            body: com.megamarket.cliente.data.remote.TransicionPedidoRequest,
            owner: String
        ) = error("n/a")
        override suspend fun listarSustituciones(estado: String?, owner: String, page: Int?, limit: Int?) = error("n/a")
        override suspend fun obtenerSustitucion(id: String, owner: String) = error("n/a")
        override suspend fun aceptarSustitucion(id: String, owner: String) = error("n/a")
        override suspend fun rechazarSustitucion(id: String, owner: String) = error("n/a")
        override suspend fun obtenerSaldoBilletera(owner: String) = error("n/a")
        override suspend fun listarMovimientosBilletera(owner: String, page: Int?, limit: Int?) = error("n/a")
        override suspend fun generarQrPago(body: GenerarQrRequest, owner: String) = error("n/a")
        override suspend fun obtenerPagoDelPedido(pedidoId: String, owner: String) = error("n/a")
        override suspend fun obtenerPago(id: String, owner: String) = error("n/a")
        override suspend fun confirmarPago(id: String, body: ConfirmarPagoRequest, owner: String) = error("n/a")
        override suspend fun autorizarBilleteraPago(id: String, body: ConfirmarPagoRequest, owner: String) =
            error("n/a")
        override suspend fun confirmarAjustePago(id: String, body: ConfirmarPagoRequest, owner: String) =
            error("n/a")
    }
}
