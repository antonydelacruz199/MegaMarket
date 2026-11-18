package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.LineaDaoMemoria
import com.megamarket.picker.data.local.LineaPickingEntity
import com.megamarket.picker.data.local.OperacionDaoMemoria
import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity
import com.megamarket.picker.data.local.PedidoDaoMemoria
import com.megamarket.picker.data.local.PerfilSesion
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.CandidatoSustitutoDto
import com.megamarket.picker.data.remote.CandidatosSustitucionResponse
import com.megamarket.picker.data.remote.EntregasListaResponse
import com.megamarket.picker.data.remote.EscaneoRequest
import com.megamarket.picker.data.remote.EscaneoResponse
import com.megamarket.picker.data.remote.FaltanteResponse
import com.megamarket.picker.data.remote.LoginRequest
import com.megamarket.picker.data.remote.LoginResponse
import com.megamarket.picker.data.remote.MeResponse
import com.megamarket.picker.data.remote.PedidoCompletoResponse
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.PropuestaSustitucionRequest
import com.megamarket.picker.data.remote.SustitucionDto
import com.megamarket.picker.data.remote.SustitucionResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SustitucionesRepositoryTest {

    @Test
    fun listaCandidatosCuandoLineaEsFaltanteConfirmada() = runBlocking {
        val api = ApiSust()
        api.candidatos = Response.success(
            CandidatosSustitucionResponse(
                detalleId = "d1",
                precioOriginalCentimos = 1000,
                tolerancia = 0.2,
                candidatos = listOf(
                    CandidatoSustitutoDto(
                        id = "prod-2",
                        nombre = "Leche alt",
                        marca = "B",
                        categoriaId = "cat",
                        pasillo = "A",
                        codigoBarras = "1",
                        precioCentimos = 1100,
                        stockDisponible = 5,
                        dentroTolerancia = true
                    )
                )
            )
        )
        val repo = repositorio(api, estadoLinea = "FALTANTE")
        val resultado = repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Exito
        assertEquals(1, resultado.valor.candidatos.size)
        assertEquals("prod-2", resultado.valor.candidatos.single().id)
        assertEquals(1000L, resultado.valor.candidatos.single().precioOriginalCentimos)
        assertEquals(1100L, resultado.valor.candidatos.single().precioSustitutoCentimos)
        assertEquals(2, resultado.valor.cantidadLinea)
    }

    @Test
    fun bloqueaSiFaltantePendienteDeSincronizacion() = runBlocking {
        val api = ApiSust()
        val lineas = LineaDaoMemoria()
        val ops = OperacionDaoMemoria()
        lineas.guardarTodas(listOf(linea("PENDIENTE")))
        ops.insertar(
            OperacionPickingEntity(
                uuidOperacion = "u1",
                ownerId = "picker-1",
                pedidoId = "p1",
                detalleId = "d1",
                operacion = OperacionPicking.MARCAR_FALTANTE,
                codigoBarras = null,
                cantidad = 0,
                estado = OperacionPicking.PENDIENTE,
                fechaCreacion = 1L
            )
        )
        val repo = repositorio(api, lineas = lineas, ops = ops)
        val error = repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.CONFLICTO, error.tipo)
        assertEquals("FALTANTE_PENDIENTE_LOCAL", error.codigo)
        assertEquals(0, api.listados)
    }

    @Test
    fun bloqueaSiLineaNoEsFaltante() = runBlocking {
        val api = ApiSust()
        val repo = repositorio(api, estadoLinea = "PENDIENTE")
        val error = repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error
        assertEquals("ESTADO_PICKING_INVALIDO", error.codigo)
        assertEquals(0, api.listados)
    }

    @Test
    fun registraPropuestaPendiente() = runBlocking {
        val api = ApiSust()
        api.propuesta = Response.success(
            SustitucionResponse(sustitucion = propuestaDto("PENDIENTE"))
        )
        val repo = repositorio(api, estadoLinea = "FALTANTE")
        val exito = repo.proponer("p1", "d1", "prod-2", 2) as ResultadoOperacion.Exito
        assertEquals("PENDIENTE", exito.valor.estado)
        assertEquals("s1", exito.valor.id)
        assertEquals("prod-2", api.ultimoProducto)
    }

    @Test
    fun conflictoPropuestaPendienteRecuperaExistente() = runBlocking {
        val api = ApiSust()
        api.propuesta = Response.error(
            409,
            """{"error":"Ya existe","codigo":"PROPUESTA_PENDIENTE","sustitucionId":"s9"}"""
                .toResponseBody(JSON)
        )
        api.consulta = Response.success(
            SustitucionResponse(sustitucion = propuestaDto("PENDIENTE").copy(id = "s9"))
        )
        val repo = repositorio(api, estadoLinea = "FALTANTE")
        val exito = repo.proponer("p1", "d1", "prod-2") as ResultadoOperacion.Exito
        assertEquals("s9", exito.valor.id)
    }

    @Test
    fun consultaEstadoAceptada() = runBlocking {
        val api = ApiSust()
        api.consulta = Response.success(
            SustitucionResponse(sustitucion = propuestaDto("ACEPTADA"))
        )
        val repo = repositorio(api, estadoLinea = "FALTANTE")
        val exito = repo.consultarSustitucion("s1") as ResultadoOperacion.Exito
        assertEquals("ACEPTADA", exito.valor.estado)
    }

    @Test
    fun erroresHttpEspecificos() = runBlocking {
        val api = ApiSust()
        val repo = repositorio(api, estadoLinea = "FALTANTE")

        api.candidatos = Response.error(401, """{"error":"Token inválido"}""".toResponseBody(JSON))
        assertEquals(TipoErrorPedido.SESION, (repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error).tipo)

        api.candidatos = Response.error(403, """{"error":"No","codigo":"PERMISO_DENEGADO"}""".toResponseBody(JSON))
        val e403 = repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.PROHIBIDO, e403.tipo)

        api.candidatos = Response.error(404, """{"error":"No","codigo":"DETALLE_NO_ENCONTRADO"}""".toResponseBody(JSON))
        assertEquals(TipoErrorPedido.NO_ENCONTRADO, (repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error).tipo)

        api.candidatos = Response.error(409, """{"error":"Conflicto","codigo":"ESTADO_PICKING_INVALIDO"}""".toResponseBody(JSON))
        assertEquals(TipoErrorPedido.CONFLICTO, (repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error).tipo)
    }

    @Test
    fun sinConexionDevuelveErrorDeRed() = runBlocking {
        val api = ApiSust()
        api.fallo = IOException("offline")
        val repo = repositorio(api, estadoLinea = "FALTANTE")
        val error = repo.listarCandidatos("p1", "d1") as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.RED, error.tipo)
        assertEquals(MensajesSustitucion.SIN_CONEXION, error.mensaje)
    }

    @Test
    fun sesionVencidaSinOwner() = runBlocking {
        val api = ApiSust()
        val almacen = Almacen(null)
        val lineas = LineaDaoMemoria()
        val ops = OperacionDaoMemoria()
        val pedidos = PedidosPickerRepository(
            api = api,
            almacen = almacen,
            baseUrl = BASE,
            pedidoDao = PedidoDaoMemoria(),
            lineaDao = lineas,
            operacionDao = ops,
            sincronizacion = SincronizacionPickingRepository(api, ops, lineas, almacen, BASE),
            programarSincronizacion = {}
        )
        val repo = SustitucionesRepository(api, almacen, lineas, ops, pedidos, BASE)
        val error = repo.proponer("p1", "d1", "prod-2") as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.SESION, error.tipo)
        assertTrue(error.mensaje.contains("Sesión"))
    }

    private fun repositorio(
        api: ApiSust,
        estadoLinea: String = "FALTANTE",
        lineas: LineaDaoMemoria = LineaDaoMemoria().also {
            runBlocking { it.guardarTodas(listOf(linea(estadoLinea))) }
        },
        ops: OperacionDaoMemoria = OperacionDaoMemoria()
    ): SustitucionesRepository {
        val almacen = Almacen("picker-1")
        val pedidos = PedidosPickerRepository(
            api = api,
            almacen = almacen,
            baseUrl = BASE,
            pedidoDao = PedidoDaoMemoria(),
            lineaDao = lineas,
            operacionDao = ops,
            sincronizacion = SincronizacionPickingRepository(api, ops, lineas, almacen, BASE),
            programarSincronizacion = {}
        )
        return SustitucionesRepository(api, almacen, lineas, ops, pedidos, BASE)
    }

    private fun linea(estado: String) = LineaPickingEntity(
        id = "d1",
        pedidoId = "p1",
        ownerId = "picker-1",
        productoId = "prod-1",
        nombre = "Leche",
        cantidad = 2,
        cantidadArmada = 0,
        estadoPicking = estado,
        pasillo = "1",
        codigoBarras = "770",
        actualizadoEn = 1L
    )

    private fun propuestaDto(estado: String) = SustitucionDto(
        id = "s1",
        pedidoDetalleId = "d1",
        pedidoId = "p1",
        productoOriginalId = "prod-1",
        productoSustitutoId = "prod-2",
        pickerId = "picker-1",
        cantidad = 2,
        precioOriginalCentimos = 1000,
        precioSustitutoCentimos = 1100,
        estado = estado,
        fechaPropuesta = "2026-11-16T17:00:00.000Z",
        fechaLimite = "2026-11-16T17:05:00.000Z",
        fechaRespuesta = null,
        respuestaAutomatica = false,
        createdAt = "2026-11-16T17:00:00.000Z",
        updatedAt = "2026-11-16T17:00:00.000Z"
    )

    private class ApiSust : ApiService {
        var candidatos: Response<CandidatosSustitucionResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var propuesta: Response<SustitucionResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var consulta: Response<SustitucionResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var listados = 0
        var ultimoProducto: String? = null
        var fallo: IOException? = null

        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ): Response<CandidatosSustitucionResponse> {
            fallo?.let { throw it }
            listados++
            return candidatos
        }

        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: PropuestaSustitucionRequest,
            owner: String
        ): Response<SustitucionResponse> {
            fallo?.let { throw it }
            ultimoProducto = body.productoSustitutoId
            return propuesta
        }

        override suspend fun obtenerSustitucion(
            id: String,
            owner: String
        ): Response<SustitucionResponse> {
            fallo?.let { throw it }
            return consulta
        }

        override suspend fun login(body: LoginRequest) = err<LoginResponse>()
        override suspend fun me(owner: String?) = err<MeResponse>()
        override suspend fun listarPedidos(estado: String, owner: String) = err<PedidosResponse>()
        override suspend fun obtenerPedido(id: String, owner: String) = err<PedidoCompletoResponse>()
        override suspend fun transicionarPedido(
            id: String,
            body: TransicionPedidoRequest,
            owner: String
        ) = err<PedidoCompletoResponse>()
        override suspend fun listarPicking(owner: String) = err<PickingListaResponse>()
        override suspend fun obtenerPicking(id: String, owner: String) = err<PickingDetalleResponse>()
        override suspend fun registrarEscaneo(
            id: String,
            detalleId: String,
            body: EscaneoRequest,
            owner: String
        ) = err<EscaneoResponse>()
        override suspend fun marcarFaltante(
            id: String,
            detalleId: String,
            owner: String
        ) = err<FaltanteResponse>()
        override suspend fun eliminarLinea(
            id: String,
            detalleId: String,
            owner: String
        ) = err<FaltanteResponse>()

        override suspend fun listarEntregas(owner: String) = err<EntregasListaResponse>()

        override suspend fun obtenerEntrega(
            id: String,
            owner: String
        ) = err<com.megamarket.picker.data.remote.EntregaDetalleResponse>()

        override suspend fun registrarEntrega(
            id: String,
            body: com.megamarket.picker.data.remote.RegistrarEntregaRequest,
            owner: String
        ) = err<com.megamarket.picker.data.remote.RegistrarEntregaResponse>()

        override suspend fun subirFotoEntrega(
            pedidoId: String,
            archivo: okhttp3.MultipartBody.Part,
            owner: String
        ) = err<com.megamarket.picker.data.remote.FotoEntregaResponse>()

        private fun <T> err(): Response<T> = Response.error(500, "{}".toResponseBody(JSON))
    }

    private class Almacen(private val activo: String?) : AlmacenSesion {
        override fun usuarioActivoId(): String? = activo
        override fun tokenActual(): String? = activo?.let { "token" }
        override fun tokenDe(usuarioId: String): String? = tokenActual()?.takeIf { usuarioId == activo }
        override fun perfil(): PerfilSesion? = null
        override fun guardar(accessToken: String, perfil: PerfilSesion) = Unit
        override fun limpiarSesion() = Unit
    }

    private companion object {
        const val BASE = "http://10.0.2.2:8010/"
        val JSON = "application/json".toMediaType()
    }
}
