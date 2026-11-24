package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.MemoriaOperacionEntrega
import com.megamarket.picker.data.local.PerfilSesion
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.CandidatosSustitucionResponse
import com.megamarket.picker.data.remote.EntregaDetalleResponse
import com.megamarket.picker.data.remote.EntregaDto
import com.megamarket.picker.data.remote.EntregasListaResponse
import com.megamarket.picker.data.remote.ConfirmarPagoRequest
import com.megamarket.picker.data.remote.ConfirmarPagoResponse
import com.megamarket.picker.data.remote.GenerarQrRequest
import com.megamarket.picker.data.remote.GenerarQrResponse
import com.megamarket.picker.data.remote.PagoPedidoResponse
import com.megamarket.picker.data.remote.PagoSimuladoDto
import com.megamarket.picker.data.remote.PedidoEstadoPagoDto
import com.megamarket.picker.data.remote.QrSimuladoDto
import com.megamarket.picker.data.remote.EscaneoRequest
import com.megamarket.picker.data.remote.EscaneoResponse
import com.megamarket.picker.data.remote.FaltanteResponse
import com.megamarket.picker.data.remote.FotoEntregaResponse
import com.megamarket.picker.data.remote.LoginRequest
import com.megamarket.picker.data.remote.LoginResponse
import com.megamarket.picker.data.remote.MeResponse
import com.megamarket.picker.data.remote.PagoDto
import com.megamarket.picker.data.remote.PedidoCompletoResponse
import com.megamarket.picker.data.remote.PedidoEntregaDto
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.PropuestaSustitucionRequest
import com.megamarket.picker.data.remote.RegistrarEntregaRequest
import com.megamarket.picker.data.remote.RegistrarEntregaResponse
import com.megamarket.picker.data.remote.SustitucionResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import com.megamarket.picker.data.repository.CODIGO_REQUIERE_PAGO
import com.megamarket.picker.data.repository.PedidosRepartoRepository
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleEntregaViewModelTest {

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
    fun pagoPendienteImpideFinalizar() {
        val api = ApiEntrega(pago = "PENDIENTE")
        val modelo = modelo(api)
        assertTrue(modelo.estado.value.pagoPendiente)
        assertFalse(modelo.estado.value.entregado)
        assertFalse(modelo.estado.value.detalle?.puedeFinalizar == true)
        modelo.establecerFoto(byteArrayOf(1, 2))
        modelo.fijarUbicacion(-12.0, -77.0)
        modelo.registrar()
        assertEquals(CODIGO_REQUIERE_PAGO, modelo.estado.value.codigoError)
        assertFalse(modelo.estado.value.entregado)
        assertEquals(0, api.registros)
        assertEquals(0, api.subidas)
    }

    @Test
    fun entregaExitosaSoloTrasConfirmacionDelServidor() {
        val api = ApiEntrega(pago = "CONFIRMADO")
        val modelo = modelo(api)
        assertFalse(modelo.estado.value.entregado)
        modelo.establecerFoto(byteArrayOf(3, 4))
        modelo.fijarUbicacion(-12.0464, -77.0428)
        modelo.registrar()
        assertTrue(modelo.estado.value.entregado)
        assertTrue(modelo.estado.value.confirmacionReciente)
        assertEquals("ENTREGADO", modelo.estado.value.detalle?.pedido?.estado)
        assertEquals(1, api.registros)
    }

    @Test
    fun errorDeRedNoMarcaEntregado() {
        val api = ApiEntrega(pago = "CONFIRMADO").apply { fallaRed = true }
        val modelo = modelo(api)
        modelo.establecerFoto(byteArrayOf(8))
        modelo.fijarUbicacion(-12.0, -77.0)
        modelo.registrar()
        assertFalse(modelo.estado.value.entregado)
        assertTrue(modelo.estado.value.error != null)
    }

    @Test
    fun contraentregaPendienteMuestraQrYMantieneBloqueo() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA")
        val modelo = modelo(api)
        assertTrue(modelo.estado.value.requierePagoQr)
        assertEquals(null, modelo.estado.value.qrPago)

        modelo.generarQrPago()

        val estado = modelo.estado.value
        assertEquals(1, api.qrGenerados)
        assertEquals(PAGO_ID, estado.qrPago?.pagoId)
        assertEquals("Código QR", estado.qrPago?.etiqueta)
        assertEquals(1500L, estado.qrPago?.montoCentimos)
        assertTrue(estado.pagoPendiente)
        assertEquals(0, api.confirmaciones)
        // Mostrar el QR no confirma nada: registrar sigue bloqueado.
        modelo.establecerFoto(byteArrayOf(1))
        modelo.fijarUbicacion(-12.0, -77.0)
        modelo.registrar()
        assertEquals(CODIGO_REQUIERE_PAGO, modelo.estado.value.codigoError)
        assertEquals(0, api.registros)
    }

    @Test
    fun pagoOnlineNoPideQrDeContraentrega() {
        val modelo = modelo(ApiEntrega(pago = "PENDIENTE", modalidad = "ONLINE"))
        assertFalse(modelo.estado.value.requierePagoQr)
    }

    @Test
    fun confirmarPagoLlamaALaApiYDesbloqueaTrasRecargar() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA")
        val modelo = modelo(api)
        modelo.generarQrPago()

        modelo.confirmarPago()

        val estado = modelo.estado.value
        assertEquals(1, api.confirmaciones)
        assertEquals(listOf("uuid-pago-1"), api.uuidsConfirmacion)
        assertFalse(estado.pagoPendiente)
        assertTrue(estado.pagoConfirmadoReciente)
        assertEquals(null, estado.qrPago)
        assertFalse(estado.requierePagoQr)
        assertEquals("CONFIRMADO", estado.detalle?.pago?.estado)

        modelo.establecerFoto(byteArrayOf(1, 2))
        modelo.fijarUbicacion(-12.0, -77.0)
        modelo.registrar()
        assertTrue(modelo.estado.value.entregado)
        assertEquals(1, api.registros)
    }

    @Test
    fun confirmarSinQrUsaPagoIdDelDetalleYDesbloquea() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA")
        val modelo = modelo(api)
        modelo.confirmarPago()
        assertEquals(1, api.confirmaciones)
        assertFalse(modelo.estado.value.pagoPendiente)
        assertTrue(modelo.estado.value.pagoConfirmadoReciente)
    }

    @Test
    fun confirmacionRechazadaPorElServidorMantieneBloqueo() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA").apply { rechazaConfirmar = true }
        val modelo = modelo(api)
        modelo.generarQrPago()

        modelo.confirmarPago()

        val estado = modelo.estado.value
        assertEquals(1, api.confirmaciones)
        assertTrue(estado.pagoPendiente)
        assertFalse(estado.pagoConfirmadoReciente)
        assertTrue(estado.errorPago != null)
        assertTrue(estado.requierePagoQr)
        // El QR sigue disponible para reintentar.
        assertEquals(PAGO_ID, estado.qrPago?.pagoId)
        modelo.establecerFoto(byteArrayOf(1))
        modelo.fijarUbicacion(-12.0, -77.0)
        modelo.registrar()
        assertEquals(0, api.registros)
    }

    @Test
    fun respuestaQueNoDiceConfirmadoNoLevantaElBloqueo() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA").apply { confirmarSinAplicar = true }
        val modelo = modelo(api)
        modelo.generarQrPago()

        modelo.confirmarPago()

        assertTrue(modelo.estado.value.pagoPendiente)
        assertFalse(modelo.estado.value.pagoConfirmadoReciente)
        assertTrue(modelo.estado.value.errorPago != null)
    }

    @Test
    fun errorDeRedEnConfirmacionVerificaConElServidorSinInventarExito() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA").apply { fallaRedConfirmar = true }
        val modelo = modelo(api)
        modelo.generarQrPago()

        modelo.confirmarPago()

        // El servidor había aplicado el cobro: GET /pagos/pedido lo confirma y el detalle recargado desbloquea.
        assertFalse(modelo.estado.value.pagoPendiente)
        assertTrue(modelo.estado.value.pagoConfirmadoReciente)
    }

    @Test
    fun reintentoTrasFalloReutilizaElMismoUuid() {
        val api = ApiEntrega(pago = "PENDIENTE", modalidad = "CONTRAENTREGA").apply { rechazaConfirmar = true }
        val modelo = modelo(api)
        modelo.generarQrPago()
        modelo.confirmarPago()
        api.rechazaConfirmar = false

        modelo.confirmarPago()

        assertEquals(listOf("uuid-pago-1", "uuid-pago-1"), api.uuidsConfirmacion)
        assertFalse(modelo.estado.value.pagoPendiente)
    }

    private fun modelo(api: ApiEntrega): DetalleEntregaViewModel {
        var contador = 0
        val repo = PedidosRepartoRepository(
            api = api,
            almacen = AlmacenReparto(),
            io = dispatcher,
            operaciones = MemoriaOperacionEntrega(),
            nuevoUuid = { UUID_OP }
        )
        return DetalleEntregaViewModel(
            SavedStateHandle(mapOf(Ruta.ARG_PEDIDO to PEDIDO)),
            repo,
            nuevoUuid = { "uuid-pago-${++contador}" }
        )
    }

    private class AlmacenReparto : AlmacenSesion {
        override fun usuarioActivoId(): String = "repartidor-1"
        override fun tokenActual(): String = "token"
        override fun tokenDe(usuarioId: String): String? = tokenActual().takeIf { usuarioId == usuarioActivoId() }
        override fun perfil(): PerfilSesion = PerfilSesion(
            "repartidor-1", "R", "r", "r@mega.test", "REPARTIDOR", null
        )
        override fun guardar(accessToken: String, perfil: PerfilSesion) = Unit
        override fun limpiarSesion() = Unit
    }

    private class ApiEntrega(
        pago: String,
        private val modalidad: String = "ONLINE"
    ) : ApiService {
        var registros = 0
        var subidas = 0
        var fallaRed = false
        /** Estado de pago que reporta el "servidor"; solo cambia cuando confirma de verdad. */
        var pago: String = pago
        var qrGenerados = 0
        var confirmaciones = 0
        val uuidsConfirmacion = mutableListOf<String>()
        var rechazaConfirmar = false
        var fallaRedConfirmar = false
        var confirmarSinAplicar = false

        override suspend fun generarQr(body: GenerarQrRequest, owner: String): Response<GenerarQrResponse> {
            qrGenerados++
            return Response.success(
                GenerarQrResponse(
                    pago = PagoSimuladoDto(
                        id = PAGO_ID,
                        pedidoId = body.pedidoId,
                        montoCentimos = 1500,
                        modalidad = modalidad,
                        estado = "QR_GENERADO"
                    ),
                    qr = QrSimuladoDto(
                        contenido = "MEGAMARKET-PAGO|PAGO=$PAGO_ID",
                        referencia = "REF-1",
                        importeCentimos = 1500,
                        pedidoId = body.pedidoId,
                        generadoEn = "2030-01-01T10:00:00.000Z",
                        expiraEn = "2030-01-01T10:15:00.000Z",
                        estado = "QR_GENERADO",
                        etiqueta = "Código QR",
                        aviso = "Ver el código no confirma el pago."
                    )
                )
            )
        }

        override suspend fun confirmarPago(
            id: String,
            body: ConfirmarPagoRequest,
            owner: String
        ): Response<ConfirmarPagoResponse> {
            confirmaciones++
            uuidsConfirmacion += body.uuidOperacion
            if (fallaRedConfirmar) {
                // El servidor sí aplicó el cobro pero la respuesta se perdió.
                pago = "CONFIRMADO"
                throw java.io.IOException("timeout")
            }
            if (rechazaConfirmar) {
                return Response.error(409, "{\"error\":\"QR_EXPIRADO\"}".toResponseBody("application/json".toMediaType()))
            }
            if (!confirmarSinAplicar) pago = "CONFIRMADO"
            return Response.success(
                ConfirmarPagoResponse(
                    pago = PagoSimuladoDto(
                        id = id,
                        modalidad = modalidad,
                        estado = if (confirmarSinAplicar) "QR_GENERADO" else "CONFIRMADO",
                        numeroOperacionBanco = "OP-1"
                    ),
                    pedido = PedidoEstadoPagoDto(id = PEDIDO, estado = "EN_CAMINO")
                )
            )
        }

        override suspend fun obtenerPagoPedido(
            pedidoId: String,
            owner: String
        ): Response<PagoPedidoResponse> = Response.success(
            PagoPedidoResponse(
                pago = PagoSimuladoDto(id = PAGO_ID, modalidad = modalidad, estado = pago),
                pedidoId = pedidoId
            )
        )

        override suspend fun obtenerEntrega(id: String, owner: String): Response<EntregaDetalleResponse> =
            Response.success(
                EntregaDetalleResponse(
                    pedido = PedidoEntregaDto(
                        id = id,
                        clienteId = "c",
                        estado = "EN_CAMINO",
                        totalCentimos = 1500,
                        franjaEntregaId = null,
                        repartidorId = owner,
                        fecha = null,
                        version = 1,
                        updatedAt = null
                    ),
                    direccion = null,
                    entrega = null,
                    pago = PagoDto(modalidad, pago, id = PAGO_ID, montoCentimos = 1500)
                )
            )

        override suspend fun subirFotoEntrega(
            pedidoId: String,
            archivo: MultipartBody.Part,
            owner: String
        ): Response<FotoEntregaResponse> {
            subidas++
            return Response.success(
                FotoEntregaResponse(
                    fotoReferencia = "entregas/$pedidoId/22222222-2222-4222-8222-222222222222.jpg",
                    pedidoId = pedidoId,
                    uso = null
                )
            )
        }

        override suspend fun registrarEntrega(
            id: String,
            body: RegistrarEntregaRequest,
            owner: String
        ): Response<RegistrarEntregaResponse> {
            registros++
            if (fallaRed) throw java.io.IOException("timeout")
            return Response.success(
                RegistrarEntregaResponse(
                    idempotente = false,
                    entrega = EntregaDto(
                        id = "e1",
                        uuidOperacion = body.uuidOperacion,
                        pedidoId = id,
                        repartidorId = owner,
                        fotoReferencia = body.fotoReferencia,
                        latitud = body.latitud,
                        longitud = body.longitud,
                        fechaEntrega = body.fechaEntrega,
                        createdAt = null
                    ),
                    pedido = PedidoEntregaDto(
                        id = id,
                        clienteId = "c",
                        estado = "ENTREGADO",
                        totalCentimos = 1500,
                        franjaEntregaId = null,
                        repartidorId = owner,
                        fecha = null,
                        version = 2,
                        updatedAt = null
                    ),
                    compatibilidadHistorica = false
                )
            )
        }

        override suspend fun login(body: LoginRequest) = error<LoginResponse>()
        override suspend fun me(owner: String?) = error<MeResponse>()
        override suspend fun listarPedidos(estado: String, owner: String, page: Int?, limit: Int?) = error<PedidosResponse>()
        override suspend fun obtenerPedido(id: String, owner: String) = error<PedidoCompletoResponse>()
        override suspend fun transicionarPedido(id: String, body: TransicionPedidoRequest, owner: String) =
            error<PedidoCompletoResponse>()
        override suspend fun listarPicking(owner: String, page: Int?, limit: Int?) = error<PickingListaResponse>()
        override suspend fun obtenerPicking(id: String, owner: String) = error<PickingDetalleResponse>()
        override suspend fun registrarEscaneo(id: String, detalleId: String, body: EscaneoRequest, owner: String) =
            error<EscaneoResponse>()
        override suspend fun marcarFaltante(id: String, detalleId: String, owner: String) = error<FaltanteResponse>()
        override suspend fun eliminarLinea(id: String, detalleId: String, owner: String) = error<FaltanteResponse>()
        override suspend fun listarSustitutos(id: String, detalleId: String, owner: String) =
            error<CandidatosSustitucionResponse>()
        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: PropuestaSustitucionRequest,
            owner: String
        ) = error<SustitucionResponse>()

        override suspend fun avisarSinStock(
            id: String,
            detalleId: String,
            owner: String
        ) = error<SustitucionResponse>()

        override suspend fun obtenerSustitucion(id: String, owner: String) = error<SustitucionResponse>()
        override suspend fun listarEntregas(owner: String, estado: String?, page: Int?, limit: Int?) = error<EntregasListaResponse>()

        private fun <T> error(): Response<T> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }

    private companion object {
        const val PAGO_ID = "44444444-4444-4444-8444-444444444444"
        const val PEDIDO = "11111111-1111-4111-8111-111111111111"
        const val UUID_OP = "33333333-3333-4333-8333-333333333333"
    }
}
