package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.Pedido
import com.megamarket.cliente.data.local.Producto
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.remote.ApiService
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
import com.megamarket.modelo.EstadoSincronizacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SustitucionesRepositoryTest {

    @Test
    fun listaPropuestasDelCliente() = runBlocking {
        val api = ApiFake()
        api.lista = Response.success(
            ListaSustitucionesResponse(sustituciones = listOf(dto("PENDIENTE")))
        )
        val exito = repositorio(api).listar() as ResultadoSustitucion.Exito
        assertEquals(1, exito.valor.size)
        assertEquals("s1", exito.valor.single().id)
        assertEquals(2000L, exito.valor.single().importeClienteCentimos)
        assertEquals(1, api.listados)
    }

    @Test
    fun consultaDetalle() = runBlocking {
        val api = ApiFake()
        api.detalle = Response.success(SustitucionDecisionResponse(sustitucion = dto("PENDIENTE")))
        val exito = repositorio(api).obtener("s1") as ResultadoSustitucion.Exito
        assertEquals("PENDIENTE", exito.valor.estado)
        assertEquals("Leche original", exito.valor.nombreOriginal)
    }

    @Test
    fun aceptaPropuesta() = runBlocking {
        val api = ApiFake()
        api.decision = Response.success(SustitucionDecisionResponse(sustitucion = dto("ACEPTADA")))
        val exito = repositorio(api).aceptar("s1") as ResultadoSustitucion.Exito
        assertEquals("ACEPTADA", exito.valor.estado)
        assertEquals(1, api.aceptadas)
    }

    @Test
    fun rechazaPropuesta() = runBlocking {
        val api = ApiFake()
        api.decision = Response.success(SustitucionDecisionResponse(sustitucion = dto("RECHAZADA")))
        val exito = repositorio(api).rechazar("s1") as ResultadoSustitucion.Exito
        assertEquals("RECHAZADA", exito.valor.estado)
        assertEquals(1, api.rechazadas)
    }

    @Test
    fun conflicto409DevuelveEstadoServidor() = runBlocking {
        val api = ApiFake()
        val cuerpo = """
            {"error":"ya resuelta","codigo":"DECISION_CONFLICTO",
             "sustitucion":{"id":"s1","pedidoDetalleId":"d1","pedidoId":"p1",
             "productoOriginalId":"prod-1","productoSustitutoId":"prod-2",
             "cantidad":2,"precioOriginalCentimos":1000,"precioSustitutoCentimos":1200,
             "estado":"ACEPTADA","fechaLimite":"2030-01-01T00:00:00.000Z",
             "respuestaAutomatica":true}}
        """.trimIndent()
        api.decision = Response.error(409, cuerpo.toResponseBody("application/json".toMediaType()))
        val exito = repositorio(api).aceptar("s1") as ResultadoSustitucion.Exito
        assertEquals("ACEPTADA", exito.valor.estado)
        assertTrue(exito.valor.respuestaAutomatica)
    }

    @Test
    fun sesionVencida401() = runBlocking {
        val api = ApiFake()
        api.lista = Response.error(401, """{"error":"token"}""".toResponseBody("application/json".toMediaType()))
        val error = repositorio(api).listar() as ResultadoSustitucion.Error
        assertEquals(TipoErrorSustitucion.SESION, error.tipo)
    }

    @Test
    fun prohibido403() = runBlocking {
        val api = ApiFake()
        api.detalle = Response.error(
            403,
            """{"error":"No autorizado","codigo":"PERMISO_DENEGADO"}"""
                .toResponseBody("application/json".toMediaType())
        )
        val error = repositorio(api).obtener("s1") as ResultadoSustitucion.Error
        assertEquals(TipoErrorSustitucion.PROHIBIDO, error.tipo)
        assertEquals("PERMISO_DENEGADO", error.codigo)
    }

    @Test
    fun noEncontrada404() = runBlocking {
        val api = ApiFake()
        api.detalle = Response.error(
            404,
            """{"error":"Sustitución no encontrada","codigo":"SUSTITUCION_NO_ENCONTRADA"}"""
                .toResponseBody("application/json".toMediaType())
        )
        val error = repositorio(api).obtener("s1") as ResultadoSustitucion.Error
        assertEquals(TipoErrorSustitucion.NO_ENCONTRADO, error.tipo)
    }

    @Test
    fun redSinConexion() = runBlocking {
        val api = ApiFake()
        api.fallarRed = true
        val error = repositorio(api).listar() as ResultadoSustitucion.Error
        assertEquals(TipoErrorSustitucion.RED, error.tipo)
    }

    @Test
    fun refrescaDetallePedidoLocal() = runBlocking {
        val api = ApiFake()
        api.pedido = Response.success(
            PedidoCompletoResponse(
                pedido = PedidoRemotoDto(
                    id = "p1",
                    estado = "LISTO",
                    totalCentimos = 5000,
                    version = 3
                )
            )
        )
        val pedidos = PedidoDaoMemoria(
            Pedido(
                id = 10,
                clienteId = 1,
                clientUuid = "uuid",
                remoteId = "p1",
                fecha = 1L,
                totalCentimos = 4000,
                estado = "EN_PICKING",
                estadoSync = EstadoSincronizacion.SINCRONIZADO,
                remoteVersion = 2
            )
        )
        val exito = repositorio(api, pedidos = pedidos).refrescarPedido("p1") as ResultadoSustitucion.Exito
        assertEquals(10L, exito.valor)
        assertEquals("LISTO", pedidos.obtenerPorRemoteId("p1")!!.estado)
        assertEquals(5000L, pedidos.obtenerPorRemoteId("p1")!!.totalCentimos)
    }

    private fun repositorio(
        api: ApiFake,
        pedidos: PedidoDao = PedidoDaoMemoria()
    ): SustitucionesRepository {
        val productos = ProductoDaoMemoria(
            listOf(
                producto("prod-1", "Leche original", "A", 1000),
                producto("prod-2", "Leche alt", "B", 1200)
            )
        )
        return SustitucionesRepository(
            api = api,
            productoDao = productos,
            pedidoDao = pedidos,
            medios = MediosImagenRepository("http://10.0.2.2:8010/"),
            usuarioRemotoId = { "cliente-1" },
            ownerLocalId = { 1L },
            io = Dispatchers.Unconfined
        )
    }

    private fun producto(remoteId: String, nombre: String, marca: String, precio: Long) =
        Producto(
            id = remoteId.hashCode().toLong().and(0x7fff),
            remoteId = remoteId,
            nombre = nombre,
            marca = marca,
            descripcion = "",
            categoriaId = 1,
            precioCentimos = precio,
            precioOfertaCentimos = null,
            stock = 10,
            imagenKey = "",
            esOferta = false,
            activo = true
        )

    private fun dto(estado: String) = SustitucionDto(
        id = "s1",
        pedidoDetalleId = "d1",
        pedidoId = "p1",
        productoOriginalId = "prod-1",
        productoSustitutoId = "prod-2",
        cantidad = 2,
        precioOriginalCentimos = 1000,
        precioSustitutoCentimos = 1200,
        estado = estado,
        fechaLimite = "2030-01-01T00:05:00.000Z",
        respuestaAutomatica = false
    )
}

private class ApiFake : ApiService {
    var lista: Response<ListaSustitucionesResponse> = Response.success(ListaSustitucionesResponse())
    var detalle: Response<SustitucionDecisionResponse> =
        Response.success(SustitucionDecisionResponse())
    var decision: Response<SustitucionDecisionResponse> =
        Response.success(SustitucionDecisionResponse())
    var pedido: Response<PedidoCompletoResponse> = Response.success(PedidoCompletoResponse())
    var fallarRed = false
    var listados = 0
    var aceptadas = 0
    var rechazadas = 0

    private fun fallarSiRed() {
        if (fallarRed) throw java.io.IOException("failed to connect")
    }

    override suspend fun reservarCarrito(id: String, body: ReservaRequest, owner: String): Response<ReservaResponse> =
        error("n/a")

    override suspend fun login(body: LoginRequest): Response<LoginResponse> = error("n/a")
    override suspend fun listarCategorias(owner: String?): Response<CategoriasResponse> = error("n/a")
    override suspend fun listarProductos(owner: String?): Response<ProductosResponse> = error("n/a")
    override suspend fun crearPedido(body: CrearPedidoRequest, owner: String?): Response<CrearPedidoResponse> =
        error("n/a")

    override suspend fun listarPedidos(
        estado: String?,
        owner: String
    ): Response<com.megamarket.cliente.data.remote.ListaPedidosResponse> = error("n/a")

    override suspend fun obtenerPedido(id: String, owner: String): Response<PedidoCompletoResponse> {
        fallarSiRed()
        return pedido
    }

    override suspend fun listarSustituciones(estado: String?, owner: String): Response<ListaSustitucionesResponse> {
        fallarSiRed()
        listados++
        return lista
    }

    override suspend fun obtenerSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> {
        fallarSiRed()
        return detalle
    }

    override suspend fun aceptarSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> {
        fallarSiRed()
        aceptadas++
        return decision
    }

    override suspend fun rechazarSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> {
        fallarSiRed()
        rechazadas++
        return decision
    }
}

private class ProductoDaoMemoria(private val items: List<Producto>) : ProductoDao {
    override fun observarTodos(): Flow<List<Producto>> = flowOf(items)
    override suspend fun ajustarDisponible(productoId: Long, cantidad: Int): Int = 0
    override fun observarActivos(): Flow<List<Producto>> = flowOf(items)
    override suspend fun obtenerActivos(): List<Producto> = items
    override suspend fun contar(): Int = items.size
    override suspend fun obtenerPorId(id: Long): Producto? = items.firstOrNull { it.id == id }
    override fun observarPorId(id: Long): Flow<Producto?> =
        flowOf(items.firstOrNull { it.id == id })
    override suspend fun obtenerPorProviderId(providerId: Long): Producto? = null
    override suspend fun obtenerPorRemoteId(remoteId: String): Producto? =
        items.firstOrNull { it.remoteId == remoteId }
    override suspend fun obtenerConRemoteId(): List<Producto> = items
    override suspend fun insertar(entidad: Producto): Long = error("n/a")
    override suspend fun actualizarProducto(entidad: Producto): Int = 0
    override suspend fun descontarStockLocal(productoId: Long, cantidad: Int): Int = 0
}

private class PedidoDaoMemoria(vararg iniciales: Pedido) : PedidoDao {
    private val items = iniciales.toMutableList()

    override fun observarDelUsuario(ownerId: Long): Flow<List<Pedido>> =
        flowOf(items.filter { it.clienteId == ownerId })

    override suspend fun reservasPendientes(): List<Pedido> = emptyList()
    override suspend fun actualizarDetalle(detalle: com.megamarket.cliente.data.local.PedidoDetalle): Int = 0
    override suspend fun insertarPedido(pedido: Pedido): Long = error("n/a")
    override suspend fun insertarDetalles(detalles: List<com.megamarket.cliente.data.local.PedidoDetalle>) = Unit
    override suspend fun insertarDireccion(direccion: com.megamarket.cliente.data.local.Direccion): Long = 0
    override suspend fun actualizarPedido(pedido: Pedido): Int {
        val idx = items.indexOfFirst { it.id == pedido.id }
        if (idx >= 0) items[idx] = pedido
        return 1
    }

    override suspend fun obtenerPedidoPorId(pedidoId: Long): Pedido? =
        items.firstOrNull { it.id == pedidoId }

    override suspend fun obtenerPorRemoteId(remoteId: String): Pedido? =
        items.firstOrNull { it.remoteId == remoteId }

    override suspend fun obtenerDetallesPedido(pedidoId: Long) =
        emptyList<com.megamarket.cliente.data.local.PedidoDetalle>()

    override suspend fun obtenerDireccionPedido(pedidoId: Long) = null
}
