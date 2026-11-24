package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.AlmacenOperacionEntrega
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.MemoriaOperacionEntrega
import com.megamarket.picker.data.local.PerfilSesion
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.CandidatosSustitucionResponse
import com.megamarket.picker.data.remote.DireccionEntregaDto
import com.megamarket.picker.data.remote.EntregaDetalleResponse
import com.megamarket.picker.data.remote.EntregaDto
import com.megamarket.picker.data.remote.EntregaListaItemDto
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
import com.megamarket.picker.data.remote.PedidoListaDto
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.PropuestaSustitucionRequest
import com.megamarket.picker.data.remote.RegistrarEntregaRequest
import com.megamarket.picker.data.remote.RegistrarEntregaResponse
import com.megamarket.picker.data.remote.SustitucionResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PedidosRepartoRepositoryTest {

    private val io = UnconfinedTestDispatcher()

    @Test
    fun cargaSeparaListoEnCaminoYEntregado() = runTest(io) {
        val api = FakeApi().apply {
            listarEntregasRespuesta = Response.success(
                EntregasListaResponse(
                    pedidos = listOf(
                        item("a", "LISTO"),
                        item("b", "EN_CAMINO"),
                        item("c", "ENTREGADO")
                    )
                )
            )
        }
        val repo = repo(api)
        val resultado = repo.cargarTablero() as ResultadoOperacion.Exito
        assertEquals(listOf("a"), resultado.valor.disponibles.map { it.id })
        assertEquals(listOf("b"), resultado.valor.enCamino.map { it.id })
        assertEquals(listOf("c"), resultado.valor.entregados.map { it.id })
    }

    @Test
    fun listarPorEstadoDevuelveSoloEsaCola() = runTest(io) {
        val api = FakeApi().apply {
            listarEntregasRespuesta = Response.success(
                EntregasListaResponse(
                    pedidos = listOf(item("b", "EN_CAMINO")),
                    pagina = 1,
                    hayMas = false,
                    total = 1
                )
            )
        }
        val resultado = repo(api).listarPorEstado("EN_CAMINO") as ResultadoOperacion.Exito
        assertEquals(listOf("b"), resultado.valor.pedidos.map { it.id })
        assertFalse(resultado.valor.hayMas)
        assertEquals("EN_CAMINO", api.ultimoEstadoListado)
    }

    @Test
    fun tomarPasaAEnCaminoSoloSiElServidorLoConfirma() = runTest(io) {
        val api = FakeApi().apply { transicionRespuesta = respuestaEnCamino() }
        val repo = repo(api)
        val resultado = repo.tomar(PEDIDO)
        assertTrue(resultado is ResultadoOperacion.Exito)
        assertEquals("EN_CAMINO", api.ultimoEstado)
        assertEquals(PEDIDO, api.ultimoPedidoId)
    }

    @Test
    fun tomarSinEstadoConfirmadoNoEsExito() = runTest(io) {
        val api = FakeApi().apply {
            transicionRespuesta = Response.success(PedidoCompletoResponse(pedido = null, pago = null))
        }
        val resultado = repo(api).tomar(PEDIDO) as ResultadoOperacion.Error
        assertEquals("ESTADO_NO_CONFIRMADO", resultado.codigo)
    }

    @Test
    fun conflictoDeAsignacionAlTomar() = runTest(io) {
        val api = FakeApi().apply {
            transicionRespuesta = http(
                403,
                """{"error":"El pedido está asignado a otro repartidor","codigo":"PEDIDO_NO_ASIGNADO"}"""
            )
        }
        val resultado = repo(api).tomar(PEDIDO) as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.PROHIBIDO, resultado.tipo)
        assertEquals("PEDIDO_NO_ASIGNADO", resultado.codigo)
    }

    @Test
    fun errorDeRedAlListar() = runTest(io) {
        val api = FakeApi().apply { fallarListado = true }
        val resultado = repo(api).cargarTablero() as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.RED, resultado.tipo)
    }

    @Test
    fun sesionVencida() = runTest(io) {
        val api = FakeApi().apply {
            listarEntregasRespuesta = http(401, """{"error":"token","codigo":"TOKEN"}""")
        }
        val resultado = repo(api).cargarTablero() as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.SESION, resultado.tipo)
    }

    @Test
    fun sinSesionFalla() = runTest(io) {
        val repo = repo(FakeApi(), id = null)
        val resultado = repo.cargarTablero() as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.SESION, resultado.tipo)
    }

    @Test
    fun pickerNoUsaOperacionesDeReparto() = runTest(io) {
        val api = FakeApi()
        val repo = repo(api, rol = "PICKER")
        assertEquals(TipoErrorPedido.PROHIBIDO, (repo.cargarTablero() as ResultadoOperacion.Error).tipo)
        assertEquals(TipoErrorPedido.PROHIBIDO, (repo.tomar(PEDIDO) as ResultadoOperacion.Error).tipo)
        assertEquals(TipoErrorPedido.PROHIBIDO, (repo.obtenerDetalle(PEDIDO) as ResultadoOperacion.Error).tipo)
        val registro = repo.registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(1),
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.PROHIBIDO, registro.tipo)
        assertEquals("PERMISO_DENEGADO", registro.codigo)
        assertEquals(0, api.listados)
        assertEquals(0, api.transiciones)
        assertEquals(0, api.detalles)
        assertEquals(0, api.subidas)
        assertEquals(0, api.registros)
    }

    @Test
    fun detalleIncluyeDireccionYPago() = runTest(io) {
        val api = FakeApi()
        val detalle = (repo(api).obtenerDetalle(PEDIDO) as ResultadoOperacion.Exito).valor
        assertEquals("Av. Primavera 123, Surco, Lima, Lima", detalle.direccion?.texto())
        assertEquals("999000111", detalle.direccion?.telefono)
        assertEquals(2590L, detalle.pedido.totalCentimos)
        assertEquals("CONTRAENTREGA", detalle.pago?.modalidad)
        assertEquals("CONFIRMADO", detalle.pago?.estado)
        assertTrue(detalle.puedeFinalizar)
        assertEquals(1, api.detalles)
    }

    @Test
    fun pagoPendienteNoLlamaAlServidorNiMarcaEntrega() = runTest(io) {
        val api = FakeApi()
        val ops = MemoriaOperacionEntrega()
        val resultado = repo(api, ops).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(1, 2, 3),
            latitud = -12.04,
            longitud = -77.03,
            estadoPedido = "EN_CAMINO",
            estadoPago = "PENDIENTE",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals(CODIGO_REQUIERE_PAGO, resultado.codigo)
        assertFalse(resultado.fotoPublicada)
        assertEquals(0, api.subidas)
        assertEquals(0, api.registros)
        assertNull(ops.uuidDe(PEDIDO))
    }

    @Test
    fun servidorExigePagoSinMarcarEntregado() = runTest(io) {
        val api = FakeApi().apply {
            registrarRespuesta = http(
                409,
                """{"error":"El pago contraentrega debe confirmarse","codigo":"ENTREGA_REQUIERE_PAGO"}"""
            )
        }
        val resultado = repo(api).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(9, 9),
            latitud = 0.0,
            longitud = 0.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = null,
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals(CODIGO_REQUIERE_PAGO, resultado.codigo)
        assertTrue(resultado.fotoPublicada)
        assertEquals(1, api.subidas)
        assertEquals(1, api.registros)
    }

    @Test
    fun entregaExitosaSoloConEstadoEntregado() = runTest(io) {
        val api = FakeApi()
        val ops = MemoriaOperacionEntrega()
        val resultado = repo(api, ops, uuid = UUID_FIJO).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(4, 5),
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Exito
        assertEquals("ENTREGADO", resultado.valor.estadoPedido)
        assertEquals(UUID_FIJO, resultado.valor.uuidOperacion)
        assertEquals(UUID_FIJO, api.uuids.single())
        assertNull(ops.uuidDe(PEDIDO))
    }

    @Test
    fun respuestaSinEntregadoNoSeTrataComoExito() = runTest(io) {
        val api = FakeApi().apply {
            registrarRespuesta = Response.success(
                registro(estado = "EN_CAMINO", idempotente = false)
            )
        }
        val ops = MemoriaOperacionEntrega()
        val resultado = repo(api, ops).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(1),
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals("ESTADO_NO_CONFIRMADO", resultado.codigo)
        assertTrue(ops.uuidDe("repartidor-1|$PEDIDO") != null)
        assertNull(ops.uuidDe(PEDIDO))
    }

    @Test
    fun otroUsuarioNoReutilizaUuidNiFoto() = runTest(io) {
        val api = FakeApi().apply { registrarFallaRed = true }
        val ops = MemoriaOperacionEntrega()
        val primero = repo(api, ops, id = "usuario-a", uuid = UUID_FIJO).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(7),
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.RED, primero.tipo)
        assertTrue(ops.fotoDe("usuario-a|$PEDIDO") != null)
        api.registrarFallaRed = false
        val registrosAntes = api.registros
        val segundo = repo(
            api,
            ops,
            id = "usuario-b",
            uuid = "44444444-4444-4444-8444-444444444444"
        ).registrar(
            pedidoId = PEDIDO,
            foto = null,
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = false
        ) as ResultadoOperacion.Error
        assertEquals("FOTO_REQUERIDA", segundo.codigo)
        assertEquals(registrosAntes, api.registros)
        assertEquals(listOf(UUID_FIJO), api.uuids)
        assertEquals(UUID_FIJO, ops.uuidDe("usuario-a|$PEDIDO"))
        assertEquals("44444444-4444-4444-8444-444444444444", ops.uuidDe("usuario-b|$PEDIDO"))
    }

    @Test
    fun reintentoConservaUuidYNoVuelveASubirLaFoto() = runTest(io) {
        val api = FakeApi().apply { registrarFallaRed = true }
        var generados = 0
        val ops = MemoriaOperacionEntrega()
        val repo = repo(api, ops) {
            generados += 1
            if (generados == 1) UUID_FIJO else "44444444-4444-4444-8444-444444444444"
        }
        val primero = repo.registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(7),
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.RED, primero.tipo)
        assertTrue(primero.fotoPublicada)
        api.registrarFallaRed = false
        api.registrarRespuesta = Response.success(registro(estado = "ENTREGADO", idempotente = true))
        val segundo = repo.registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(7),
            latitud = -12.1,
            longitud = -77.1,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = false
        ) as ResultadoOperacion.Exito
        assertTrue(segundo.valor.idempotente)
        assertEquals(listOf(UUID_FIJO, UUID_FIJO), api.uuids)
        assertEquals(1, api.subidas)
        assertEquals(1, generados)
    }

    @Test
    fun pedidoQueNoEstaEnCaminoNoSeEntrega() = runTest(io) {
        val api = FakeApi()
        val resultado = repo(api).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(1),
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "LISTO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals("PEDIDO_NO_EN_CAMINO", resultado.codigo)
        assertEquals(0, api.registros)
    }

    @Test
    fun gpsInvalidoNoEnvia() = runTest(io) {
        val api = FakeApi()
        val resultado = repo(api).registrar(
            pedidoId = PEDIDO,
            foto = byteArrayOf(1),
            latitud = 120.0,
            longitud = 0.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        ) as ResultadoOperacion.Error
        assertEquals("GPS_INVALIDO", resultado.codigo)
        assertEquals(0, api.subidas)
    }

    @Test
    fun compresionRespetaElTopeAnunciadoPorLaApi() = runTest(io) {
        val api = FakeApi()
        api.subirCola += http(413, """{"error":"El archivo supera el máximo de 100000 bytes","codigo":"ARCHIVO_DEMASIADO_GRANDE"}""")
        val limites = mutableListOf<Int>()
        val repo = PedidosRepartoRepository(
            api = api,
            almacen = almacen("REPARTIDOR"),
            io = io,
            compresor = CompresorFoto { _, limite ->
                limites += limite
                byteArrayOf(1)
            },
            nuevoUuid = { UUID_FIJO }
        )
        val resultado = repo.registrar(
            pedidoId = PEDIDO,
            foto = ByteArray(200) { 3 },
            latitud = -12.0,
            longitud = -77.0,
            estadoPedido = "EN_CAMINO",
            estadoPago = "CONFIRMADO",
            nuevaFoto = true
        )
        assertTrue(resultado is ResultadoOperacion.Exito)
        assertEquals(listOf(LIMITE_FOTO_BYTES, 100000), limites)
        assertEquals(2, api.subidas)
    }

    @Test
    fun reglasDePagoGpsYLimite() {
        assertTrue(pagoEstaPendiente("PENDIENTE"))
        assertFalse(pagoEstaPendiente("CONFIRMADO"))
        assertFalse(pagoEstaPendiente(null))
        assertFalse(puedeRegistrarEntrega("LISTO", "CONFIRMADO", false))
        assertFalse(puedeRegistrarEntrega("EN_CAMINO", "PENDIENTE", false))
        assertTrue(puedeRegistrarEntrega("EN_CAMINO", null, false))
        assertFalse(gpsValido(91.0, 0.0))
        assertTrue(gpsValido(-12.0464, -77.0428))
        assertEquals(100000, limiteBytesAnunciado("El archivo supera el máximo de 100000 bytes"))
        assertTrue(esUuidOperacion(UUID_FIJO))
        assertFalse(fotoReferenciaDelPedido("entregas/otro/22222222-2222-4222-8222-222222222222.jpg", PEDIDO))
    }

    private fun repo(
        api: FakeApi,
        ops: AlmacenOperacionEntrega = MemoriaOperacionEntrega(),
        id: String? = "repartidor-1",
        rol: String = "REPARTIDOR",
        uuid: String = UUID_FIJO
    ) = PedidosRepartoRepository(
        api = api,
        almacen = almacen(rol, id),
        io = io,
        operaciones = ops,
        nuevoUuid = { uuid }
    )

    private fun repo(
        api: FakeApi,
        ops: AlmacenOperacionEntrega,
        uuid: () -> String
    ) = PedidosRepartoRepository(
        api = api,
        almacen = almacen("REPARTIDOR"),
        io = io,
        operaciones = ops,
        nuevoUuid = uuid
    )

    private fun almacen(rol: String, id: String? = "repartidor-1") = object : AlmacenSesion {
        override fun usuarioActivoId(): String? = id
        override fun tokenActual(): String? = id?.let { "token" }
        override fun tokenDe(usuarioId: String): String? = tokenActual()?.takeIf { usuarioId == id }
        override fun perfil(): PerfilSesion? = id?.let {
            PerfilSesion(it, "Repartidor", "reparto", "reparto@mega.test", rol, null)
        }
        override fun guardar(accessToken: String, perfil: PerfilSesion) = Unit
        override fun limpiarSesion() = Unit
    }

    private fun item(id: String, estado: String) = EntregaListaItemDto(
        pedido = PedidoEntregaDto(
            id = id,
            clienteId = "c",
            estado = estado,
            totalCentimos = 1000,
            franjaEntregaId = null,
            repartidorId = if (estado == "LISTO") null else "repartidor-1",
            fecha = "2026-01-01T10:00:00Z",
            version = 1,
            updatedAt = null
        ),
        entrega = null
    )

    private fun respuestaEnCamino(): Response<PedidoCompletoResponse> = Response.success(
        PedidoCompletoResponse(
            pedido = PedidoListaDto(
                id = PEDIDO,
                clienteId = "c",
                estado = "EN_CAMINO",
                totalCentimos = 1000,
                franjaEntregaId = null,
                pickerId = null,
                fecha = null,
                version = 2
            ),
            pago = PagoDto("CONTRAENTREGA", "CONFIRMADO")
        )
    )

    private fun registro(estado: String, idempotente: Boolean) = RegistrarEntregaResponse(
        idempotente = idempotente,
        entrega = EntregaDto(
            id = "ent-1",
            uuidOperacion = UUID_FIJO,
            pedidoId = PEDIDO,
            repartidorId = "repartidor-1",
            fotoReferencia = fotoDe(PEDIDO),
            latitud = -12.0,
            longitud = -77.0,
            fechaEntrega = "2026-11-17T12:00:00.000Z",
            createdAt = null
        ),
        pedido = PedidoEntregaDto(
            id = PEDIDO,
            clienteId = "c",
            estado = estado,
            totalCentimos = 1000,
            franjaEntregaId = null,
            repartidorId = "repartidor-1",
            fecha = null,
            version = 3,
            updatedAt = null
        ),
        compatibilidadHistorica = false
    )

    private fun <T> http(codigo: Int, cuerpo: String): Response<T> =
        Response.error(codigo, cuerpo.toResponseBody("application/json".toMediaType()))

    private class FakeApi : ApiService {

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
        var listarEntregasRespuesta: Response<EntregasListaResponse> =
            Response.success(EntregasListaResponse(emptyList()))
        var transicionRespuesta: Response<PedidoCompletoResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        var detalleRespuesta: Response<EntregaDetalleResponse>? = null
        var registrarRespuesta: Response<RegistrarEntregaResponse>? = null
        val subirCola = ArrayDeque<Response<FotoEntregaResponse>>()
        var fallarListado = false
        var registrarFallaRed = false
        var listados = 0
        var transiciones = 0
        var detalles = 0
        var subidas = 0
        var registros = 0
        var ultimoEstado: String? = null
        var ultimoPedidoId: String? = null
        val uuids = mutableListOf<String>()

        override suspend fun login(body: LoginRequest): Response<LoginResponse> = error("n/a")
        override suspend fun me(owner: String?): Response<MeResponse> = error("n/a")
        override suspend fun listarPedidos(estado: String, owner: String, page: Int?, limit: Int?): Response<PedidosResponse> = error("n/a")
        override suspend fun obtenerPedido(id: String, owner: String): Response<PedidoCompletoResponse> = error("n/a")
        override suspend fun transicionarPedido(
            id: String,
            body: TransicionPedidoRequest,
            owner: String
        ): Response<PedidoCompletoResponse> {
            transiciones++
            ultimoPedidoId = id
            ultimoEstado = body.estado
            return transicionRespuesta
        }
        override suspend fun listarPicking(owner: String, page: Int?, limit: Int?): Response<PickingListaResponse> = error("n/a")
        override suspend fun obtenerPicking(id: String, owner: String): Response<PickingDetalleResponse> = error("n/a")
        override suspend fun registrarEscaneo(
            id: String,
            detalleId: String,
            body: EscaneoRequest,
            owner: String
        ): Response<EscaneoResponse> = error("n/a")
        override suspend fun marcarFaltante(id: String, detalleId: String, owner: String): Response<FaltanteResponse> =
            error("n/a")
        override suspend fun eliminarLinea(id: String, detalleId: String, owner: String): Response<FaltanteResponse> =
            error("n/a")
        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ): Response<CandidatosSustitucionResponse> = error("n/a")
        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: PropuestaSustitucionRequest,
            owner: String
        ): Response<SustitucionResponse> = error("n/a")

        override suspend fun avisarSinStock(
            id: String,
            detalleId: String,
            owner: String
        ): Response<SustitucionResponse> = error("n/a")

        override suspend fun obtenerSustitucion(id: String, owner: String): Response<SustitucionResponse> = error("n/a")
        var ultimoEstadoListado: String? = null
        override suspend fun listarEntregas(owner: String, estado: String?, page: Int?, limit: Int?): Response<EntregasListaResponse> {
            listados++
            ultimoEstadoListado = estado
            if (fallarListado) throw IOException("timeout")
            return listarEntregasRespuesta
        }
        override suspend fun obtenerEntrega(id: String, owner: String): Response<EntregaDetalleResponse> {
            detalles++
            return detalleRespuesta ?: Response.success(
                EntregaDetalleResponse(
                    pedido = PedidoEntregaDto(
                        id = id,
                        clienteId = "c",
                        estado = "EN_CAMINO",
                        totalCentimos = 2590,
                        franjaEntregaId = null,
                        repartidorId = "repartidor-1",
                        fecha = "2026-11-17T15:00:00Z",
                        version = 4,
                        updatedAt = null
                    ),
                    direccion = DireccionEntregaDto(
                        departamento = "Lima",
                        provincia = "Lima",
                        distrito = "Surco",
                        direccion = "Av. Primavera 123",
                        telefono = "999000111"
                    ),
                    entrega = null,
                    pago = PagoDto("CONTRAENTREGA", "CONFIRMADO")
                )
            )
        }
        override suspend fun registrarEntrega(
            id: String,
            body: RegistrarEntregaRequest,
            owner: String
        ): Response<RegistrarEntregaResponse> {
            registros++
            uuids += body.uuidOperacion
            if (registrarFallaRed) throw IOException("timeout")
            return registrarRespuesta ?: Response.success(
                RegistrarEntregaResponse(
                    idempotente = false,
                    entrega = EntregaDto(
                        id = "ent-1",
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
                        totalCentimos = 2590,
                        franjaEntregaId = null,
                        repartidorId = owner,
                        fecha = null,
                        version = 5,
                        updatedAt = null
                    ),
                    compatibilidadHistorica = false
                )
            )
        }
        override suspend fun subirFotoEntrega(
            pedidoId: String,
            archivo: MultipartBody.Part,
            owner: String
        ): Response<FotoEntregaResponse> {
            subidas++
            if (subirCola.isNotEmpty()) return subirCola.removeFirst()
            return Response.success(
                FotoEntregaResponse(
                    fotoReferencia = fotoDe(pedidoId),
                    pedidoId = pedidoId,
                    uso = "Usar fotoReferencia al registrar la entrega"
                )
            )
        }
    }

    private companion object {
        const val PEDIDO = "11111111-1111-4111-8111-111111111111"
        const val UUID_FIJO = "33333333-3333-4333-8333-333333333333"

        fun fotoDe(pedidoId: String) =
            "entregas/$pedidoId/22222222-2222-4222-8222-222222222222.jpg"
    }
}
