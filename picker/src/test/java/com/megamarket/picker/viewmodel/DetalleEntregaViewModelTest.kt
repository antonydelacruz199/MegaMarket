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

    private fun modelo(api: ApiEntrega): DetalleEntregaViewModel {
        val repo = PedidosRepartoRepository(
            api = api,
            almacen = AlmacenReparto(),
            io = dispatcher,
            operaciones = MemoriaOperacionEntrega(),
            nuevoUuid = { UUID_OP }
        )
        return DetalleEntregaViewModel(
            SavedStateHandle(mapOf(Ruta.ARG_PEDIDO to PEDIDO)),
            repo
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

    private class ApiEntrega(private val pago: String) : ApiService {
        var registros = 0
        var subidas = 0
        var fallaRed = false

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
                    pago = PagoDto("ONLINE", pago)
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
        override suspend fun listarPedidos(estado: String, owner: String) = error<PedidosResponse>()
        override suspend fun obtenerPedido(id: String, owner: String) = error<PedidoCompletoResponse>()
        override suspend fun transicionarPedido(id: String, body: TransicionPedidoRequest, owner: String) =
            error<PedidoCompletoResponse>()
        override suspend fun listarPicking(owner: String) = error<PickingListaResponse>()
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
        override suspend fun obtenerSustitucion(id: String, owner: String) = error<SustitucionResponse>()
        override suspend fun listarEntregas(owner: String) = error<EntregasListaResponse>()

        private fun <T> error(): Response<T> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }

    private companion object {
        const val PEDIDO = "11111111-1111-4111-8111-111111111111"
        const val UUID_OP = "33333333-3333-4333-8333-333333333333"
    }
}
