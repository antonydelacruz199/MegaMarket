package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.ConfirmarPagoRequest
import com.megamarket.cliente.data.remote.GenerarQrRequest
import com.megamarket.cliente.data.remote.CategoriasResponse
import com.megamarket.cliente.data.remote.CrearPedidoRequest
import com.megamarket.cliente.data.remote.CrearPedidoResponse
import com.megamarket.cliente.data.remote.ListaSustitucionesResponse
import com.megamarket.cliente.data.remote.LoginRequest
import com.megamarket.cliente.data.remote.LoginResponse
import com.megamarket.cliente.data.remote.PedidoCompletoResponse
import com.megamarket.cliente.data.remote.PedidoRemotoDto
import com.megamarket.cliente.data.remote.ProductosResponse
import com.megamarket.cliente.data.remote.ReservaRequest
import com.megamarket.cliente.data.remote.ReservaResponse
import com.megamarket.cliente.data.remote.SustitucionDecisionResponse
import com.megamarket.cliente.data.remote.SustitucionDto
import com.megamarket.cliente.data.repository.MediosImagenRepository
import com.megamarket.cliente.data.repository.MensajesSustitucionCliente
import com.megamarket.cliente.data.repository.SustitucionesRepository
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleSustitucionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var modelo: DetalleSustitucionViewModel? = null

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun limpiar() {
        modelo?.detenerSeguimiento()
        modelo = null
        Dispatchers.resetMain()
    }

    @Test
    fun cargaDetallePendienteConCuentaRegresiva() = runBlocking {
        val api = ApiVm()
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("PENDIENTE")))
        val vm = modelo(api)
        esperar { vm.estado.value.propuesta != null }
        assertEquals("PENDIENTE", vm.estado.value.propuesta?.estado)
        assertNotNull(vm.estado.value.tiempoRestanteTexto)
        vm.detenerSeguimiento()
    }

    @Test
    fun aceptaYQuedaDefinitiva() = runBlocking {
        val api = ApiVm()
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("PENDIENTE")))
        api.decision = Response.success(SustitucionDecisionResponse(sustitucion = dto("ACEPTADA")))
        api.pedido = Response.success(
            PedidoCompletoResponse(pedido = PedidoRemotoDto(id = "p1", estado = "EN_PICKING"))
        )
        val vm = modelo(api)
        esperar { vm.estado.value.propuesta?.estado == "PENDIENTE" }
        vm.aceptar()
        esperar { vm.estado.value.propuesta?.estado == "ACEPTADA" }
        assertEquals("ACEPTADA", vm.estado.value.propuesta?.estado)
        assertEquals(1, api.aceptadas)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun evitaDecisionDuplicadaMientrasEnvia() = runBlocking {
        val api = ApiVm()
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("PENDIENTE")))
        api.puertaDecision = CompletableDeferred()
        val vm = modelo(api)
        esperar { vm.estado.value.propuesta?.estado == "PENDIENTE" }
        vm.aceptar()
        esperar { vm.estado.value.enviando }
        vm.aceptar()
        vm.rechazar()
        assertTrue(vm.estado.value.enviando)
        assertTrue(api.decisionesIniciadas <= 1)
        api.puertaDecision!!.complete(
            Response.success(SustitucionDecisionResponse(sustitucion = dto("ACEPTADA")))
        )
        esperar { !vm.estado.value.enviando && vm.estado.value.propuesta?.estado == "ACEPTADA" }
        assertEquals(1, api.aceptadas)
        assertEquals(0, api.rechazadas)
    }

    @Test
    fun propuestaYaResueltaNoReenvia() = runBlocking {
        val api = ApiVm()
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("RECHAZADA")))
        val vm = modelo(api)
        esperar { vm.estado.value.propuesta?.estado == "RECHAZADA" }
        vm.aceptar()
        assertEquals(MensajesSustitucionCliente.NO_PENDIENTE, vm.estado.value.error)
        assertEquals(0, api.aceptadas)
    }

    @Test
    fun actualizaEstadoCuandoCambiaEnServidor() = runBlocking {
        val api = ApiVm()
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("PENDIENTE")))
        val vm = modelo(api)
        esperar { vm.estado.value.propuesta?.estado == "PENDIENTE" }
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("CANCELADA")))
        vm.consultarEstado()
        esperar { vm.estado.value.propuesta?.estado == "CANCELADA" }
        assertEquals("CANCELADA", vm.estado.value.propuesta?.estado)
        vm.detenerSeguimiento()
    }

    private fun modelo(api: ApiVm, intervalo: Long = 60_000L): DetalleSustitucionViewModel {
        val repo = SustitucionesRepository(
            api = api,
            productoDao = ProductoDaoVacio(),
            pedidoDao = PedidoDaoVacio(),
            medios = MediosImagenRepository("http://10.0.2.2:8010/"),
            usuarioRemotoId = { "cliente-1" },
            ownerLocalId = { 1L },
            io = Dispatchers.Unconfined
        )
        val handle = SavedStateHandle(mapOf(Ruta.ARG_SUSTITUCION to "s1"))
        return DetalleSustitucionViewModel(
            savedStateHandle = handle,
            sustituciones = repo,
            intervaloConsultaMs = intervalo,
            intervaloRelojMs = 60_000L
        ).also { modelo = it }
    }

    private suspend fun esperar(condicion: () -> Boolean) {
        repeat(80) {
            if (condicion()) return
            delay(25)
        }
        throw AssertionError("Condición no alcanzada")
    }

    private fun dto(estado: String) = SustitucionDto(
        id = "s1",
        pedidoDetalleId = "d1",
        pedidoId = "p1",
        productoOriginalId = "prod-1",
        productoSustitutoId = "prod-2",
        cantidad = 2,
        precioOriginalCentimos = 1000,
        precioSustitutoCentimos = 900,
        estado = estado,
        fechaLimite = "2030-01-01T00:05:00.000Z",
        respuestaAutomatica = false
    )
}

private class ApiVm : ApiService {
    var detalle: Response<SustitucionDecisionResponse> =
        Response.success(SustitucionDecisionResponse())
    var decision: Response<SustitucionDecisionResponse> =
        Response.success(SustitucionDecisionResponse())
    var pedido: Response<PedidoCompletoResponse> = Response.success(PedidoCompletoResponse())
    var puertaDecision: CompletableDeferred<Response<SustitucionDecisionResponse>>? = null
    var aceptadas = 0
    var rechazadas = 0
    var decisionesIniciadas = 0

    override suspend fun reservarCarrito(id: String, body: ReservaRequest, owner: String) = error("n/a")
    override suspend fun login(body: LoginRequest): Response<LoginResponse> = error("n/a")
    override suspend fun listarCategorias(owner: String?): Response<CategoriasResponse> = error("n/a")
    override suspend fun listarProductos(owner: String?, page: Int?, limit: Int?, q: String?): Response<ProductosResponse> = error("n/a")
    override suspend fun listarFranjas(owner: String?) = error("n/a")
    override suspend fun obtenerFranja(id: String, owner: String?) = error("n/a")
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
    override suspend fun obtenerPedido(id: String, owner: String) = pedido
    override suspend fun transicionarPedido(
        id: String,
        body: com.megamarket.cliente.data.remote.TransicionPedidoRequest,
        owner: String
    ) = error("n/a")
    override suspend fun listarSustituciones(estado: String?, owner: String, page: Int?, limit: Int?) =
        Response.success(ListaSustitucionesResponse())

    override suspend fun obtenerSustitucion(id: String, owner: String) = detalle

    override suspend fun aceptarSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> {
        decisionesIniciadas++
        aceptadas++
        puertaDecision?.let { return it.await() }
        return decision
    }

    override suspend fun rechazarSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> {
        decisionesIniciadas++
        rechazadas++
        puertaDecision?.let { return it.await() }
        return decision
    }

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

private class ProductoDaoVacio : ProductoDao {
    override fun observarTodos(): Flow<List<com.megamarket.cliente.data.local.Producto>> = flowOf(emptyList())
    override suspend fun ajustarDisponible(productoId: Long, cantidad: Int) = 0
    override fun observarActivos() = flowOf(emptyList<com.megamarket.cliente.data.local.Producto>())
    override suspend fun obtenerActivos() = emptyList<com.megamarket.cliente.data.local.Producto>()
    override suspend fun contar() = 0
    override suspend fun obtenerPorId(id: Long) = null
    override fun observarPorId(id: Long) = flowOf(null)
    override suspend fun obtenerPorProviderId(providerId: Long) = null
    override suspend fun obtenerPorRemoteId(remoteId: String) = null
    override suspend fun obtenerConRemoteId() = emptyList<com.megamarket.cliente.data.local.Producto>()
    override suspend fun insertar(entidad: com.megamarket.cliente.data.local.Producto) = 0L
    override suspend fun actualizarProducto(entidad: com.megamarket.cliente.data.local.Producto) = 0
    override suspend fun descontarStockLocal(productoId: Long, cantidad: Int) = 0
}

private class PedidoDaoVacio : PedidoDao {
    override fun observarDelUsuario(ownerId: Long) = flowOf(emptyList<com.megamarket.cliente.data.local.Pedido>())
    override suspend fun listarDelUsuario(ownerId: Long) = emptyList<com.megamarket.cliente.data.local.Pedido>()
    override suspend fun reservasPendientes() = emptyList<com.megamarket.cliente.data.local.Pedido>()
    override suspend fun actualizarDetalle(detalle: com.megamarket.cliente.data.local.PedidoDetalle) = 0
    override suspend fun insertarPedido(pedido: com.megamarket.cliente.data.local.Pedido) = 0L
    override suspend fun insertarDetalles(detalles: List<com.megamarket.cliente.data.local.PedidoDetalle>) = Unit
    override suspend fun insertarDireccion(direccion: com.megamarket.cliente.data.local.Direccion) = 0L
    override suspend fun actualizarPedido(pedido: com.megamarket.cliente.data.local.Pedido) = 0
    override suspend fun obtenerPedidoPorId(pedidoId: Long) = null
    override suspend fun obtenerPorRemoteId(remoteId: String) = null
    override suspend fun obtenerDetallesPedido(pedidoId: Long) =
        emptyList<com.megamarket.cliente.data.local.PedidoDetalle>()
    override suspend fun obtenerDireccionPedido(pedidoId: Long) = null
}
