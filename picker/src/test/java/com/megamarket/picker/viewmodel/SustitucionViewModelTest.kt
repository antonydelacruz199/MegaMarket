package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.LineaDaoMemoria
import com.megamarket.picker.data.local.LineaPickingEntity
import com.megamarket.picker.data.local.OperacionDaoMemoria
import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity
import com.megamarket.picker.data.local.PedidoDaoMemoria
import com.megamarket.picker.data.local.PedidoPickingEntity
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
import com.megamarket.picker.data.remote.PagoDto
import com.megamarket.picker.data.remote.PedidoCompletoResponse
import com.megamarket.picker.data.remote.PedidoPickingDto
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.PropuestaSustitucionRequest
import com.megamarket.picker.data.remote.SustitucionDto
import com.megamarket.picker.data.remote.SustitucionResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import com.megamarket.picker.data.repository.MensajesSustitucion
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.SincronizacionPickingRepository
import com.megamarket.picker.data.repository.SustitucionesRepository
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SustitucionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var modeloActivo: SustitucionViewModel? = null

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun limpiar() {
        modeloActivo?.detenerSeguimiento()
        modeloActivo = null
        Dispatchers.resetMain()
    }

    @Test
    fun cargaCandidatosParaFaltanteConfirmado() = runBlocking {
        val api = ApiVm()
        api.candidatos = okCandidatos()
        val modelo = modelo(api)
        esperar { modelo.estado.value.candidatos.isNotEmpty() }
        assertEquals(1, modelo.estado.value.candidatos.size)
        assertEquals("Leche alt", modelo.estado.value.candidatos.single().nombre)
        assertFalse(modelo.estado.value.faltantePendienteLocal)
    }

    @Test
    fun muestraFaltantePendienteYNoConsultaApi() = runBlocking {
        val api = ApiVm()
        val lineas = LineaDaoMemoria()
        val ops = OperacionDaoMemoria()
        lineas.guardarTodas(listOf(lineaCache("PENDIENTE")))
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
        val modelo = modelo(api, lineas, ops)
        esperar { modelo.estado.value.faltantePendienteLocal }
        assertEquals(MensajesSustitucion.FALTANTE_PENDIENTE, modelo.estado.value.error)
        assertEquals(0, api.listados)
    }

    @Test
    fun proponeYQuedaPendiente() = runBlocking {
        val api = ApiVm()
        api.candidatos = okCandidatos()
        api.propuesta = Response.success(SustitucionResponse(sustitucion = dto("PENDIENTE")))
        val modelo = modelo(api)
        esperar { modelo.estado.value.candidatos.isNotEmpty() }
        modelo.seleccionar("prod-2")
        modelo.proponer()
        esperar { modelo.estado.value.propuesta?.estado == "PENDIENTE" }
        assertEquals("PENDIENTE", modelo.estado.value.propuesta?.estado)
        assertNotNull(modelo.estado.value.tiempoRestanteTexto)
        assertEquals(1, api.propuestas)
        modelo.detenerSeguimiento()
    }

    @Test
    fun evitaPropuestasDuplicadasMientrasEnvia() = runBlocking {
        val api = ApiVm()
        api.candidatos = okCandidatos()
        api.puertaPropuesta = CompletableDeferred()
        val modelo = modelo(api)
        esperar { modelo.estado.value.candidatos.isNotEmpty() }
        modelo.seleccionar("prod-2")
        assertEquals("prod-2", modelo.estado.value.seleccionadoId)
        modelo.proponer()
        esperar { modelo.estado.value.enviando }
        modelo.proponer()
        modelo.proponer()
        assertTrue(modelo.estado.value.enviando)
        assertTrue(api.propuestasIniciadas <= 1)
        api.puertaPropuesta!!.complete(
            Response.success(SustitucionResponse(sustitucion = dto("PENDIENTE")))
        )
        esperar { !modelo.estado.value.enviando && modelo.estado.value.propuesta != null }
        assertEquals(1, api.propuestas)
        assertEquals(1, api.propuestasIniciadas)
        modelo.detenerSeguimiento()
    }

    @Test
    fun consultaManualDetectaAceptacionYRefrescaDetalle() = runBlocking {
        val api = ApiVm()
        api.candidatos = okCandidatos()
        api.propuesta = Response.success(SustitucionResponse(sustitucion = dto("PENDIENTE")))
        api.consulta = Response.success(SustitucionResponse(sustitucion = dto("ACEPTADA")))
        api.preparacion = okPreparacion("SUSTITUIDO")
        val modelo = modelo(api)
        esperar { modelo.estado.value.candidatos.isNotEmpty() }
        modelo.seleccionar("prod-2")
        modelo.proponer()
        esperar { modelo.estado.value.propuesta?.estado == "PENDIENTE" }
        modelo.detenerSeguimiento()
        modelo.consultarEstado()
        esperar { modelo.estado.value.propuesta?.estado == "ACEPTADA" }
        assertEquals("ACEPTADA", modelo.estado.value.propuesta?.estado)
        esperar { modelo.estado.value.detalleActualizado }
        assertTrue(api.consultas >= 1)
        assertTrue(api.preparaciones >= 1)
    }

    @Test
    fun rechazoYCancelacionActualizanEstado() = runBlocking {
        val api = ApiVm()
        api.candidatos = okCandidatos()
        api.propuesta = Response.success(SustitucionResponse(sustitucion = dto("PENDIENTE")))
        val modelo = modelo(api)
        esperar { modelo.estado.value.candidatos.isNotEmpty() }
        modelo.seleccionar("prod-2")
        modelo.proponer()
        esperar { modelo.estado.value.propuesta != null }
        modelo.detenerSeguimiento()

        api.consulta = Response.success(SustitucionResponse(sustitucion = dto("RECHAZADA")))
        modelo.consultarEstado()
        esperar { modelo.estado.value.propuesta?.estado == "RECHAZADA" }

        api.consulta = Response.success(SustitucionResponse(sustitucion = dto("CANCELADA")))
        modelo.consultarEstado()
        esperar { modelo.estado.value.propuesta?.estado == "CANCELADA" }
        assertEquals("CANCELADA", modelo.estado.value.propuesta?.estado)
    }

    @Test
    fun sesionVencidaAlListar() = runBlocking {
        val api = ApiVm()
        api.candidatos = Response.error(401, """{"error":"Token inválido"}""".toResponseBody(JSON))
        val modelo = modelo(api)
        esperar { modelo.estado.value.sesionVencida }
        assertTrue(modelo.estado.value.sesionVencida)
    }

    private suspend fun esperar(intentos: Int = 40, condicion: () -> Boolean) {
        repeat(intentos) {
            if (condicion()) return
            delay(25)
        }
        assertTrue("Condición de espera no cumplida", condicion())
    }

    private fun modelo(
        api: ApiVm,
        lineas: LineaDaoMemoria = LineaDaoMemoria().also {
            runBlocking { it.guardarTodas(listOf(lineaCache("FALTANTE"))) }
        },
        ops: OperacionDaoMemoria = OperacionDaoMemoria()
    ): SustitucionViewModel {
        val almacen = Almacen()
        val pedidosDao = PedidoDaoMemoria()
        runBlocking {
            pedidosDao.guardar(
                PedidoPickingEntity(
                    id = "p1",
                    ownerId = "picker-1",
                    totalCentimos = 100,
                    fecha = null,
                    modalidadPago = "CONTRAENTREGA",
                    estadoPago = "PENDIENTE",
                    actualizadoEn = 1L
                )
            )
        }
        val pedidos = PedidosPickerRepository(
            api = api,
            almacen = almacen,
            baseUrl = BASE,
            pedidoDao = pedidosDao,
            lineaDao = lineas,
            operacionDao = ops,
            sincronizacion = SincronizacionPickingRepository(api, ops, lineas, almacen, BASE),
            programarSincronizacion = {}
        )
        val sust = SustitucionesRepository(api, almacen, lineas, ops, pedidos, BASE)
        return SustitucionViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Ruta.ARG_PEDIDO to "p1", Ruta.ARG_DETALLE to "d1")
            ),
            sustituciones = sust,
            pedidos = pedidos,
            intervaloConsultaMs = 60_000L,
            intervaloRelojMs = 60_000L
        ).also { modeloActivo = it }
    }

    private fun lineaCache(estado: String) = LineaPickingEntity(
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

    private fun okCandidatos() = Response.success(
        CandidatosSustitucionResponse(
            detalleId = "d1",
            precioOriginalCentimos = 1000,
            tolerancia = 0.2,
            candidatos = listOf(
                CandidatoSustitutoDto(
                    id = "prod-2",
                    nombre = "Leche alt",
                    marca = "B",
                    categoriaId = "c",
                    pasillo = "A",
                    codigoBarras = "1",
                    precioCentimos = 1100,
                    stockDisponible = 4,
                    dentroTolerancia = true
                )
            )
        )
    )

    private fun dto(estado: String) = SustitucionDto(
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
        fechaLimite = "2099-11-16T17:05:00.000Z",
        fechaRespuesta = null,
        respuestaAutomatica = false,
        createdAt = "2026-11-16T17:00:00.000Z",
        updatedAt = "2026-11-16T17:00:00.000Z"
    )

    private fun okPreparacion(estadoLinea: String) = Response.success(
        PickingDetalleResponse(
            pedido = PedidoPickingDto(
                id = "p1",
                clienteId = "c1",
                estado = "EN_PICKING",
                totalCentimos = 100,
                franjaEntregaId = null,
                pickerId = "picker-1",
                preferenciaSustitucion = "ACEPTAR",
                fecha = null,
                version = 1,
                updatedAt = null
            ),
            detalles = listOf(
                com.megamarket.picker.data.remote.LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 2,
                    cantidadArmada = 0,
                    estadoPicking = estadoLinea,
                    precioUnitarioCentimos = 1000,
                    subtotalCentimos = 2000,
                    pasillo = "1",
                    codigoBarras = "770",
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            ),
            pago = PagoDto("CONTRAENTREGA", "PENDIENTE")
        )
    )

    private class ApiVm : ApiService {

        override suspend fun generarQr(
            body: com.megamarket.picker.data.remote.GenerarQrRequest,
            owner: String
        ): retrofit2.Response<com.megamarket.picker.data.remote.GenerarQrResponse> = throw UnsupportedOperationException()
        override suspend fun confirmarPago(
            id: String,
            body: com.megamarket.picker.data.remote.ConfirmarPagoRequest,
            owner: String
        ): retrofit2.Response<com.megamarket.picker.data.remote.ConfirmarPagoResponse> = throw UnsupportedOperationException()
        override suspend fun obtenerPagoPedido(
            pedidoId: String,
            owner: String
        ): retrofit2.Response<com.megamarket.picker.data.remote.PagoPedidoResponse> = throw UnsupportedOperationException()
        var candidatos: Response<CandidatosSustitucionResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var propuesta: Response<SustitucionResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var consulta: Response<SustitucionResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var preparacion: Response<PickingDetalleResponse> =
            Response.error(500, "{}".toResponseBody(JSON))
        var puertaPropuesta: CompletableDeferred<Response<SustitucionResponse>>? = null
        var listados = 0
        var propuestas = 0
        var propuestasIniciadas = 0
        var consultas = 0
        var preparaciones = 0

        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ): Response<CandidatosSustitucionResponse> {
            listados++
            return candidatos
        }

        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: PropuestaSustitucionRequest,
            owner: String
        ): Response<SustitucionResponse> {
            propuestasIniciadas++
            val respuesta = puertaPropuesta?.await() ?: propuesta
            propuestas++
            return respuesta
        }
        override suspend fun avisarSinStock(
            id: String,
            detalleId: String,
            owner: String
        ): Response<SustitucionResponse> = Response.error(501, okhttp3.ResponseBody.create(null, ByteArray(0)))

        override suspend fun obtenerSustitucion(
            id: String,
            owner: String
        ): Response<SustitucionResponse> {
            consultas++
            return consulta
        }

        override suspend fun obtenerPicking(
            id: String,
            owner: String
        ): Response<PickingDetalleResponse> {
            preparaciones++
            return preparacion
        }

        override suspend fun login(body: LoginRequest) = err<LoginResponse>()
        override suspend fun me(owner: String?) = err<MeResponse>()
        override suspend fun listarPedidos(estado: String, owner: String, page: Int?, limit: Int?) = err<PedidosResponse>()
        override suspend fun obtenerPedido(id: String, owner: String) = err<PedidoCompletoResponse>()
        override suspend fun transicionarPedido(
            id: String,
            body: TransicionPedidoRequest,
            owner: String
        ) = err<PedidoCompletoResponse>()
        override suspend fun listarPicking(owner: String, page: Int?, limit: Int?) = err<PickingListaResponse>()
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

        override suspend fun listarEntregas(owner: String, estado: String?, page: Int?, limit: Int?) = err<EntregasListaResponse>()

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

    private class Almacen : AlmacenSesion {
        override fun usuarioActivoId(): String = "picker-1"
        override fun tokenActual(): String = "token"
        override fun tokenDe(usuarioId: String): String? =
            tokenActual().takeIf { usuarioId == usuarioActivoId() }
        override fun perfil(): PerfilSesion? = null
        override fun guardar(accessToken: String, perfil: PerfilSesion) = Unit
        override fun limpiarSesion() = Unit
    }

    private companion object {
        const val BASE = "http://10.0.2.2:8010/"
        val JSON = "application/json".toMediaType()
    }
}
