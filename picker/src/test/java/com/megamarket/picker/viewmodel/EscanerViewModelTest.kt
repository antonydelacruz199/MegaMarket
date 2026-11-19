package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.LineaDaoMemoria
import com.megamarket.picker.data.local.LineaPickingEntity
import com.megamarket.picker.data.local.OperacionDaoMemoria
import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.PedidoDaoMemoria
import com.megamarket.picker.data.local.PerfilSesion
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.EscaneoDto
import com.megamarket.picker.data.remote.EscaneoRequest
import com.megamarket.picker.data.remote.EscaneoResponse
import com.megamarket.picker.data.remote.FaltanteResponse
import com.megamarket.picker.data.remote.LineaPickingDto
import com.megamarket.picker.data.remote.LoginRequest
import com.megamarket.picker.data.remote.LoginResponse
import com.megamarket.picker.data.remote.MeResponse
import com.megamarket.picker.data.remote.PagoDto
import com.megamarket.picker.data.remote.PedidoCompletoResponse
import com.megamarket.picker.data.remote.PedidoPickingDto
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.ResultadoSincronizacion
import com.megamarket.picker.data.repository.SincronizacionPickingRepository
import com.megamarket.picker.data.repository.TipoResultadoEscaneo
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EscanerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun lecturasDuplicadasEnvianUnaSolaSolicitud() = runBlocking {
        val api = ApiEscaner()
        api.puerta = CompletableDeferred()
        val modelo = modelo(api)
        modelo.codigoDetectado("770001")
        modelo.codigoDetectado("770001")
        modelo.codigoDetectado("770001")
        assertEquals(1, api.envios.size)
        api.puerta?.complete(exito("770001", armada = 1, estado = "PENDIENTE"))
        Unit
    }

    @Test
    fun reintentoSincronizaElUuidPersistido() = runBlocking {
        val api = ApiEscaner()
        api.fallo = IOException("timeout")
        val modelo = modelo(api)
        modelo.enviarManual("770001")
        assertEquals(TipoResultadoEscaneo.PENDIENTE_LOCAL, modelo.estado.value.resultado)
        val uuid = api.ops.todas().single().uuidOperacion
        api.fallo = null
        api.envios.clear()
        api.respuesta = exito("770001", armada = 1, estado = "PENDIENTE")
        val sync = SincronizacionPickingRepository(
            api = api,
            operacionDao = api.ops,
            lineaDao = api.lineas,
            almacen = AlmacenPrueba(),
            baseUrl = "http://10.0.2.2:8010/",
            io = dispatcher
        )
        assertTrue(sync.sincronizarPendientes() is ResultadoSincronizacion.Exito)
        assertEquals(listOf(uuid), api.envios.map { it.uuidOperacion })
        assertEquals(OperacionPicking.SINCRONIZADO, api.ops.porUuid(uuid)?.estado)
    }

    @Test
    fun entradaManualConservaCerosIniciales() = runBlocking {
        val api = ApiEscaner()
        api.respuesta = exito("00770001", armada = 1, estado = "PENDIENTE")
        val modelo = modelo(api)
        modelo.enviarManual("00770001")
        assertEquals("00770001", api.envios.single().codigoBarras)
    }

    @Test
    fun escaneoCorrectoActualizaArmadaConfirmada() = runBlocking {
        val api = ApiEscaner()
        api.respuesta = exito("770001", armada = 2, estado = "ARMADO")
        val modelo = modelo(api, armadaInicial = 0)
        modelo.enviarManual("770001")
        assertEquals(TipoResultadoEscaneo.CORRECTO, modelo.estado.value.resultado)
        assertEquals(2, modelo.estado.value.cantidadArmada)
        assertEquals(0, modelo.estado.value.cantidadPendienteLocal)
    }

    @Test
    fun escaneoIncorrectoVibraSinSubirArmada() = runBlocking {
        val api = ApiEscaner()
        api.respuesta = Response.error(
            409,
            """
            {"error":"Código de barras no corresponde","codigo":"CODIGO_INCORRECTO",
             "escaneo":{"resultado":"INCORRECTO","codigoBarras":"999","cantidad":1},
             "detalle":{"id":"d1","pedidoId":"p1","productoId":"prod-1","nombreProducto":"Leche",
             "cantidad":3,"cantidadArmada":0,"estadoPicking":"PENDIENTE"}}
            """.trimIndent().toResponseBody(JSON)
        )
        val modelo = modelo(api, armadaInicial = 0)
        modelo.codigoDetectado("999")
        assertEquals(TipoResultadoEscaneo.INCORRECTO, modelo.estado.value.resultado)
        assertTrue(modelo.estado.value.vibrar)
        assertEquals(0, modelo.estado.value.cantidadArmada)
    }

    @Test
    fun sinInternetDejaPendienteLocal() = runBlocking {
        val api = ApiEscaner()
        api.fallo = IOException("offline")
        val modelo = modelo(api)
        modelo.codigoDetectado("770001")
        assertEquals(TipoResultadoEscaneo.PENDIENTE_LOCAL, modelo.estado.value.resultado)
        assertTrue(modelo.estado.value.cantidadPendienteLocal >= 1)
        assertFalse(modelo.estado.value.sesionVencida)
    }

    @Test
    fun respuesta401MarcaSesionYConservaCola() = runBlocking {
        val api = ApiEscaner()
        api.respuesta = Response.error(
            401,
            """{"error":"Token inválido o expirado"}""".toResponseBody(JSON)
        )
        val modelo = modelo(api)
        modelo.enviarManual("770001")
        assertEquals(TipoResultadoEscaneo.SESION_VENCIDA, modelo.estado.value.resultado)
        assertTrue(modelo.estado.value.sesionVencida)
        assertEquals(OperacionPicking.PENDIENTE, api.ops.porUuid(api.envios.single().uuidOperacion)?.estado)
    }

    private fun modelo(
        api: ApiEscaner,
        armadaInicial: Int = 0,
        estadoInicial: String = "PENDIENTE"
    ): EscanerViewModel {
        api.armadaInicial = armadaInicial
        api.estadoInicial = estadoInicial
        runBlocking {
            api.lineas.guardarTodas(
                listOf(
                    LineaPickingEntity(
                        id = "d1",
                        pedidoId = "p1",
                        ownerId = "picker-1",
                        productoId = "prod-1",
                        nombre = "Leche",
                        cantidad = 3,
                        cantidadArmada = armadaInicial,
                        estadoPicking = estadoInicial,
                        pasillo = "1",
                        codigoBarras = "770001",
                        actualizadoEn = 1L
                    )
                )
            )
        }
        val sync = SincronizacionPickingRepository(
            api = api,
            operacionDao = api.ops,
            lineaDao = api.lineas,
            almacen = AlmacenPrueba(),
            baseUrl = "http://10.0.2.2:8010/",
            io = dispatcher
        )
        val repositorio = PedidosPickerRepository(
            api = api,
            almacen = AlmacenPrueba(),
            baseUrl = "http://10.0.2.2:8010/",
            pedidoDao = PedidoDaoMemoria(),
            lineaDao = api.lineas,
            operacionDao = api.ops,
            sincronizacion = sync,
            programarSincronizacion = {},
            io = dispatcher
        )
        return EscanerViewModel(
            SavedStateHandle(
                mapOf(
                    Ruta.ARG_PEDIDO to "p1",
                    Ruta.ARG_DETALLE to "d1"
                )
            ),
            repositorio
        )
    }

    private fun exito(codigo: String, armada: Int, estado: String): Response<EscaneoResponse> =
        Response.success(
            EscaneoResponse(
                idempotente = false,
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = codigo, cantidad = 1),
                detalle = LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 3,
                    cantidadArmada = armada,
                    estadoPicking = estado,
                    precioUnitarioCentimos = 100,
                    subtotalCentimos = 300,
                    pasillo = "1",
                    codigoBarras = codigo,
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            )
        )

    private class ApiEscaner : ApiService {

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
        val envios = mutableListOf<EscaneoRequest>()
        val ops = OperacionDaoMemoria()
        val lineas = LineaDaoMemoria()
        var respuesta: Response<EscaneoResponse> = Response.error(500, "{}".toResponseBody(JSON))
        var fallo: IOException? = null
        var puerta: CompletableDeferred<Response<EscaneoResponse>>? = null
        var armadaInicial: Int = 0
        var estadoInicial: String = "PENDIENTE"

        override suspend fun registrarEscaneo(
            id: String,
            detalleId: String,
            body: EscaneoRequest,
            owner: String
        ): Response<EscaneoResponse> {
            envios += body
            fallo?.let { throw it }
            return puerta?.await() ?: respuesta
        }

        override suspend fun marcarFaltante(
            id: String,
            detalleId: String,
            owner: String
        ): Response<FaltanteResponse> = Response.error(500, "{}".toResponseBody(JSON))

        override suspend fun eliminarLinea(
            id: String,
            detalleId: String,
            owner: String
        ): Response<FaltanteResponse> = Response.error(500, "{}".toResponseBody(JSON))

        override suspend fun obtenerPicking(id: String, owner: String): Response<PickingDetalleResponse> =
            Response.success(
                PickingDetalleResponse(
                    pedido = PedidoPickingDto(
                        id = "p1",
                        clienteId = "c1",
                        estado = "EN_PICKING",
                        totalCentimos = 100,
                        franjaEntregaId = null,
                        pickerId = "picker-1",
                        preferenciaSustitucion = null,
                        fecha = null,
                        version = 1,
                        updatedAt = null
                    ),
                    detalles = listOf(
                        LineaPickingDto(
                            id = "d1",
                            pedidoId = "p1",
                            productoId = "prod-1",
                            nombreProducto = "Leche",
                            cantidad = 3,
                            cantidadArmada = armadaInicial,
                            estadoPicking = estadoInicial,
                            precioUnitarioCentimos = 100,
                            subtotalCentimos = 300,
                            pasillo = "1",
                            codigoBarras = "770001",
                            fechaEscaneo = null,
                            escaneadoPor = null
                        )
                    ),
                    pago = PagoDto("CONTRAENTREGA", "PENDIENTE")
                )
            )

        override suspend fun login(body: LoginRequest): Response<LoginResponse> = error()
        override suspend fun me(owner: String?): Response<MeResponse> = error()
        override suspend fun listarPedidos(estado: String, owner: String): Response<PedidosResponse> = error()
        override suspend fun obtenerPedido(id: String, owner: String): Response<PedidoCompletoResponse> = error()
        override suspend fun transicionarPedido(
            id: String,
            body: TransicionPedidoRequest,
            owner: String
        ): Response<PedidoCompletoResponse> = error()
        override suspend fun listarPicking(owner: String): Response<PickingListaResponse> = error()
        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ) = error<com.megamarket.picker.data.remote.CandidatosSustitucionResponse>()
        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: com.megamarket.picker.data.remote.PropuestaSustitucionRequest,
            owner: String
        ) = error<com.megamarket.picker.data.remote.SustitucionResponse>()
        override suspend fun listarEntregas(owner: String) =
            throw UnsupportedOperationException("no usado")

        override suspend fun obtenerEntrega(
            id: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun registrarEntrega(
            id: String,
            body: com.megamarket.picker.data.remote.RegistrarEntregaRequest,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun subirFotoEntrega(
            pedidoId: String,
            archivo: okhttp3.MultipartBody.Part,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun obtenerSustitucion(
            id: String,
            owner: String
        ) = error<com.megamarket.picker.data.remote.SustitucionResponse>()

        private fun <T> error(): Response<T> = Response.error(500, "{}".toResponseBody(JSON))
    }

    private class AlmacenPrueba : AlmacenSesion {
        override fun usuarioActivoId(): String = "picker-1"
        override fun tokenActual(): String = "token"
        override fun tokenDe(usuarioId: String): String? = tokenActual().takeIf { usuarioId == usuarioActivoId() }
        override fun perfil(): PerfilSesion? = null
        override fun guardar(accessToken: String, perfil: PerfilSesion) = Unit
        override fun limpiarSesion() = Unit
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
