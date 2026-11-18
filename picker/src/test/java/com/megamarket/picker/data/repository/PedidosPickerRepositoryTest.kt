package com.megamarket.picker.data.repository

import com.google.gson.Gson
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
import com.megamarket.picker.data.remote.PedidoListaDto
import com.megamarket.picker.data.remote.PedidoPickingDto
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingItemDto
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class PedidosPickerRepositoryTest {

    @Test
    fun disponiblesExcluyenOnlineSinPagoYConservanContraentregaYPagado() = runBlocking {
        val api = ApiFalsa()
        api.listas["CONFIRMADO"] = Response.success(
            PedidosResponse(
                listOf(
                    pedido("online", "CONFIRMADO", "2026-11-15T12:00:00.000Z"),
                    pedido("contra", "CONFIRMADO", "2026-11-15T09:00:00.000Z"),
                    pedido("legado", "CONFIRMADO", "2026-11-15T08:00:00.000Z")
                )
            )
        )
        api.listas["PAGADO"] = Response.success(
            PedidosResponse(listOf(pedido("pagado", "PAGADO", "2026-11-15T11:00:00.000Z")))
        )
        api.detalles["online"] = detalle("online", PagoDto("ONLINE", "PENDIENTE"))
        api.detalles["contra"] = detalle("contra", PagoDto("CONTRAENTREGA", "PENDIENTE"))
        api.detalles["legado"] = detalle("legado", null)
        api.picking = Response.success(PickingListaResponse(emptyList()))

        val tablero = (repositorio(api).cargarTablero() as ResultadoOperacion.Exito).valor
        assertEquals(listOf("legado", "contra", "pagado"), tablero.disponibles.map { it.id })
        assertEquals("CONTRAENTREGA", tablero.disponibles[1].modalidad)
    }

    @Test
    fun asignadosVienenDePickingYElDetalleRespetaElPasillo() = runBlocking {
        val api = ApiFalsa()
        api.listas["CONFIRMADO"] = Response.success(PedidosResponse(emptyList()))
        api.listas["PAGADO"] = Response.success(PedidosResponse(emptyList()))
        api.picking = Response.success(
            PickingListaResponse(
                listOf(
                    PickingItemDto(
                        pedido = picking("p1"),
                        detalles = listOf(linea("l1", "A"), linea("l2", "B"))
                    )
                )
            )
        )
        api.preparaciones["p1"] = Response.success(
            PickingDetalleResponse(
                pedido = picking("p1"),
                detalles = listOf(
                    linea("l1", "Leche", "1", "770001"),
                    linea("l2", "Pan", "2"),
                    linea("l3", "Arroz", null)
                ),
                pago = PagoDto("CONTRAENTREGA", "PENDIENTE")
            )
        )
        val repo = repositorio(api)
        val tablero = (repo.cargarTablero() as ResultadoOperacion.Exito).valor
        assertEquals(2, tablero.asignados.single().cantidadLineas)
        val preparacion = (repo.obtenerPreparacion("p1") as ResultadoOperacion.Exito).valor
        assertEquals(listOf("1", "2", SIN_PASILLO), preparacion.grupos.map { it.pasillo })
        assertEquals(2, preparacion.grupos[0].lineas.single().cantidad)
        assertEquals(1, preparacion.grupos[0].lineas.single().cantidadArmada)
        assertEquals("PENDIENTE", preparacion.grupos[0].lineas.single().estadoPicking)
        assertEquals("770001", preparacion.grupos[0].lineas.single().codigoBarras)
        assertTrue(
            preparacion.grupos[0].lineas.single().imagenUrl!!
                .endsWith("/api/medios/productos/prod-l1?download=1")
        )
    }

    @Test
    fun tomarEnviaEnPicking() = runBlocking {
        val api = ApiFalsa()
        api.transicion = Response.success(PedidoCompletoResponse(pedido("p1", "EN_PICKING"), null))
        val resultado = repositorio(api).tomar("p1")
        assertTrue(resultado is ResultadoOperacion.Exito)
        assertEquals("EN_PICKING", api.estadoEnviado)
        assertEquals("picker-1", api.ownerEnviado)
    }

    @Test
    fun otroPickerProduce403() = runBlocking {
        val api = ApiFalsa()
        api.transicion = httpError(
            403,
            """{"error":"No tienes permiso sobre este pedido"}"""
        )
        val resultado = repositorio(api).tomar("p1") as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.PROHIBIDO, resultado.tipo)
        assertEquals("No tienes permiso sobre este pedido", resultado.mensaje)
    }

    @Test
    fun pagoPendienteProduce409() = runBlocking {
        val api = ApiFalsa()
        api.transicion = httpError(
            409,
            """{"error":"El pago online debe confirmarse antes de iniciar el picking","codigo":"PICKING_REQUIERE_PAGO"}"""
        )
        val resultado = repositorio(api).tomar("p1") as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.CONFLICTO, resultado.tipo)
        assertEquals("PICKING_REQUIERE_PAGO", resultado.codigo)
    }

    @Test
    fun tokenInvalidoEsSesionVencida() = runBlocking {
        val api = ApiFalsa()
        api.listas["CONFIRMADO"] = httpError(401, """{"error":"Token inválido o expirado"}""")
        val resultado = repositorio(api).cargarTablero() as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.SESION, resultado.tipo)
    }

    @Test
    fun sinSesionNoLlamaALaApi() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        almacen.activo = null
        val resultado = repositorio(api, almacen).cargarTablero() as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.SESION, resultado.tipo)
        assertEquals(0, api.listasConsultadas)
    }

    @Test
    fun falloDeRed() = runBlocking {
        val api = ApiFalsa()
        api.falloRed = IOException("timeout")
        val resultado = repositorio(api).cargarTablero() as ResultadoOperacion.Error
        assertEquals(TipoErrorPedido.RED, resultado.tipo)
        assertEquals(MensajesSesion.SIN_CONEXION, resultado.mensaje)
    }

    @Test
    fun pedidoAjenoNoSeOfrece() = runBlocking {
        val api = ApiFalsa()
        api.listas["CONFIRMADO"] = Response.success(
            PedidosResponse(listOf(pedido("ajeno", "CONFIRMADO", pickerId = "otro")))
        )
        api.listas["PAGADO"] = Response.success(PedidosResponse(emptyList()))
        api.picking = Response.success(PickingListaResponse(emptyList()))
        val tablero = (repositorio(api).cargarTablero() as ResultadoOperacion.Exito).valor
        assertTrue(tablero.disponibles.isEmpty())
        assertNull(api.detalles["ajeno"])
    }

    @Test
    fun escaneoCorrectoDevuelveLaLineaDelServidor() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = Response.success(
            EscaneoResponse(
                idempotente = false,
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "770001", cantidad = 1),
                detalle = linea("d1", "Leche", codigoBarras = "770001").copy(
                    cantidadArmada = 2,
                    estadoPicking = "ARMADO"
                )
            )
        )
        val ctx = contexto(api)
        sembrarLinea(ctx, armada = 1)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.CORRECTO, resultado.tipo)
        assertEquals(2, resultado.linea?.cantidadArmada)
        assertEquals("ARMADO", resultado.linea?.estadoPicking)
        assertFalse(resultado.vibrar)
        assertEquals(UUID_ESCANEO, api.escaneosEnviados.single().uuidOperacion)
        assertEquals(1, api.escaneosEnviados.single().cantidad)
        assertEquals(OperacionPicking.SINCRONIZADO, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
    }

    @Test
    fun escaneoIncorrectoVibraYNoSubeArmadaConfirmada() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = httpError(
            409,
            """
            {"error":"Código de barras no corresponde al producto solicitado","codigo":"CODIGO_INCORRECTO",
             "escaneo":{"resultado":"INCORRECTO","codigoBarras":"999999","cantidad":1},
             "detalle":{"id":"d1","pedidoId":"p1","productoId":"prod-d1","nombreProducto":"Leche","cantidad":2,
             "cantidadArmada":0,"estadoPicking":"PENDIENTE","pasillo":"1","codigoBarras":"770001"}}
            """.trimIndent()
        )
        val ctx = contexto(api)
        sembrarLinea(ctx, armada = 0)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "999999")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.INCORRECTO, resultado.tipo)
        assertTrue(resultado.vibrar)
        assertEquals(0, ctx.lineas.obtener("d1", "picker-1")?.cantidadArmada)
        assertEquals(OperacionPicking.SINCRONIZADO, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
        assertEquals(OperacionPicking.RESULTADO_INCORRECTO, ctx.ops.porUuid(UUID_ESCANEO)?.resultado)
    }

    @Test
    fun cantidadExcedidaLocalNoEnviaAlServidor() = runBlocking {
        val api = ApiFalsa()
        val ctx = contexto(api)
        sembrarLinea(ctx, armada = 2, solicitada = 2)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.CANTIDAD_EXCEDIDA, resultado.tipo)
        assertTrue(api.escaneosEnviados.isEmpty())
        assertNull(ctx.ops.porUuid(UUID_ESCANEO))
    }

    @Test
    fun codigoConCerosInicialesViajaComoTexto() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = Response.success(
            EscaneoResponse(
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "00770001"),
                detalle = linea("d1", "Leche").copy(cantidadArmada = 1)
            )
        )
        val ctx = contexto(api)
        sembrarLinea(ctx)
        ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "  00770001")
        assertEquals("00770001", api.escaneosEnviados.single().codigoBarras)
        val json = Gson().toJson(api.escaneosEnviados.single())
        assertTrue(json.contains("\"codigoBarras\":\"00770001\""))
    }

    @Test
    fun pedidoNoAsignadoQuedaRechazadoSinBorrarUuid() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = httpError(
            403,
            """{"error":"El pedido no está asignado a este picker","codigo":"PEDIDO_NO_ASIGNADO"}"""
        )
        val ctx = contexto(api)
        sembrarLinea(ctx)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.PEDIDO_NO_ASIGNADO, resultado.tipo)
        assertEquals(OperacionPicking.ERROR, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
        assertEquals("PEDIDO_NO_ASIGNADO", ctx.ops.porUuid(UUID_ESCANEO)?.resultado)
        assertEquals(0, ctx.ops.cantidadPendienteEscaneos("d1", "picker-1"))
    }

    @Test
    fun cantidadExcedidaDelServidorQuedaVisibleYNoSumaPendiente() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = httpError(
            409,
            """{"error":"La cantidad armada no puede superar la solicitada","codigo":"CANTIDAD_EXCEDIDA"}"""
        )
        val ctx = contexto(api)
        sembrarLinea(ctx, armada = 0, solicitada = 2)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.CANTIDAD_EXCEDIDA, resultado.tipo)
        assertEquals("CANTIDAD_EXCEDIDA", resultado.codigo)
        assertEquals(0, resultado.linea?.cantidadArmada)
        assertEquals(0, resultado.cantidadPendienteLocal)
        assertEquals(OperacionPicking.ERROR, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
        assertEquals("CANTIDAD_EXCEDIDA", ctx.ops.porUuid(UUID_ESCANEO)?.resultado)
    }

    @Test
    fun respuesta401ConservaLaOperacionPendiente() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = httpError(401, """{"error":"Token inválido o expirado"}""")
        val ctx = contexto(api)
        sembrarLinea(ctx)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.SESION_VENCIDA, resultado.tipo)
        assertEquals(OperacionPicking.PENDIENTE, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
        assertEquals(UUID_ESCANEO, ctx.ops.porUuid(UUID_ESCANEO)?.uuidOperacion)
    }

    @Test
    fun sinInternetGuardaPendienteLocal() = runBlocking {
        val api = ApiFalsa()
        api.falloRed = IOException("timeout")
        val ctx = contexto(api)
        sembrarLinea(ctx, armada = 0)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.PendienteLocal
        assertEquals(1, resultado.cantidadPendienteLocal)
        assertEquals(0, resultado.cantidadArmada)
        assertEquals(OperacionPicking.PENDIENTE, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
        assertEquals("770001", ctx.ops.porUuid(UUID_ESCANEO)?.codigoBarras)
    }

    @Test
    fun reintentoReutilizaElMismoUuidPersistido() = runBlocking {
        val api = ApiFalsa()
        api.falloRed = IOException("timeout")
        val ctx = contexto(api)
        sembrarLinea(ctx)
        ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
        api.falloRed = null
        api.escaneos = Response.success(
            EscaneoResponse(
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "770001"),
                detalle = linea("d1", "Leche").copy(cantidadArmada = 1, estadoPicking = "PENDIENTE")
            )
        )
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.CORRECTO, resultado.tipo)
        assertEquals(1, api.escaneosEnviados.size)
        assertEquals(UUID_ESCANEO, api.escaneosEnviados.single().uuidOperacion)
    }

    @Test
    fun cambioDeUsuarioNoEnviaOperacionesAjenas() = runBlocking {
        val api = ApiFalsa()
        api.falloRed = IOException("offline")
        val ctx = contexto(api)
        sembrarLinea(ctx)
        ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
        api.falloRed = null
        ctx.almacen.activo = "otro-picker"
        ctx.sync.sincronizarPendientes()
        assertTrue(api.escaneosEnviados.isEmpty())
        assertEquals(OperacionPicking.PENDIENTE, ctx.ops.porUuid(UUID_ESCANEO)?.estado)
    }

    @Test
    fun respuestaIdempotenteUsaLaCantidadDelServidor() = runBlocking {
        val api = ApiFalsa()
        api.escaneos = Response.success(
            EscaneoResponse(
                idempotente = true,
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "770001", cantidad = 1),
                detalle = linea("d1", "Leche").copy(cantidadArmada = 1, estadoPicking = "PENDIENTE")
            )
        )
        val ctx = contexto(api)
        sembrarLinea(ctx, armada = 0)
        val resultado = ctx.repo.registrarEscaneo("p1", "d1", UUID_ESCANEO, "770001")
            as ResultadoEscaneo.Confirmado
        assertEquals(TipoResultadoEscaneo.CORRECTO, resultado.tipo)
        assertEquals(1, resultado.linea?.cantidadArmada)
    }

    private data class Ctx(
        val repo: PedidosPickerRepository,
        val sync: SincronizacionPickingRepository,
        val ops: OperacionDaoMemoria,
        val lineas: LineaDaoMemoria,
        val almacen: AlmacenMemoria
    )

    private fun contexto(api: ApiFalsa, almacen: AlmacenMemoria = AlmacenMemoria()): Ctx {
        val ops = OperacionDaoMemoria()
        val lineas = LineaDaoMemoria()
        val pedidos = PedidoDaoMemoria()
        val sync = SincronizacionPickingRepository(
            api = api,
            operacionDao = ops,
            lineaDao = lineas,
            almacen = almacen,
            baseUrl = "http://10.0.2.2:8010/"
        )
        val repo = PedidosPickerRepository(
            api = api,
            almacen = almacen,
            baseUrl = "http://10.0.2.2:8010/",
            pedidoDao = pedidos,
            lineaDao = lineas,
            operacionDao = ops,
            sincronizacion = sync,
            programarSincronizacion = {}
        )
        return Ctx(repo, sync, ops, lineas, almacen)
    }

    private fun repositorio(api: ApiFalsa, almacen: AlmacenMemoria = AlmacenMemoria()) =
        contexto(api, almacen).repo

    private suspend fun sembrarLinea(
        ctx: Ctx,
        armada: Int = 0,
        solicitada: Int = 2
    ) {
        ctx.lineas.guardarTodas(
            listOf(
                LineaPickingEntity(
                    id = "d1",
                    pedidoId = "p1",
                    ownerId = "picker-1",
                    productoId = "prod-d1",
                    nombre = "Leche",
                    cantidad = solicitada,
                    cantidadArmada = armada,
                    estadoPicking = "PENDIENTE",
                    pasillo = "1",
                    codigoBarras = "770001",
                    actualizadoEn = 1L
                )
            )
        )
    }
    private fun pedido(
        id: String,
        estado: String,
        fecha: String = "2026-11-15T10:00:00.000Z",
        pickerId: String? = null
    ) = PedidoListaDto(
        id = id,
        clienteId = "c1",
        estado = estado,
        totalCentimos = 2500,
        franjaEntregaId = null,
        pickerId = pickerId,
        fecha = fecha,
        version = 1
    )

    private fun detalle(id: String, pago: PagoDto?) =
        Response.success(PedidoCompletoResponse(pedido(id, "CONFIRMADO"), pago))

    private fun picking(id: String) = PedidoPickingDto(
        id = id,
        clienteId = "c1",
        estado = "EN_PICKING",
        totalCentimos = 2500,
        franjaEntregaId = null,
        pickerId = "picker-1",
        preferenciaSustitucion = "RECHAZAR",
        fecha = "2026-11-15T10:00:00.000Z",
        version = 1,
        updatedAt = null
    )

    private fun linea(
        id: String,
        nombre: String,
        pasillo: String? = "1",
        codigoBarras: String? = null
    ) = LineaPickingDto(
        id = id,
        pedidoId = "p1",
        productoId = "prod-$id",
        nombreProducto = nombre,
        cantidad = 2,
        cantidadArmada = 1,
        estadoPicking = "PENDIENTE",
        precioUnitarioCentimos = 1000,
        subtotalCentimos = 2000,
        pasillo = pasillo,
        codigoBarras = codigoBarras,
        fechaEscaneo = null,
        escaneadoPor = null
    )

    private fun <T> httpError(codigo: Int, json: String): Response<T> =
        Response.error(codigo, json.toResponseBody("application/json".toMediaType()))

    private inner class ApiFalsa : ApiService {
        val listas = mutableMapOf<String, Response<PedidosResponse>>()
        val detalles = mutableMapOf<String, Response<PedidoCompletoResponse>>()
        val preparaciones = mutableMapOf<String, Response<PickingDetalleResponse>>()
        var picking: Response<PickingListaResponse> = Response.success(PickingListaResponse(emptyList()))
        var transicion: Response<PedidoCompletoResponse> = httpError(500, "{}")
        var estadoEnviado: String? = null
        var ownerEnviado: String? = null
        var listasConsultadas: Int = 0
        var falloRed: IOException? = null
        var escaneos: Response<EscaneoResponse> = httpError(500, "{}")
        val escaneosEnviados = mutableListOf<EscaneoRequest>()

        override suspend fun login(body: LoginRequest): Response<LoginResponse> = httpError(500, "{}")
        override suspend fun me(owner: String?): Response<MeResponse> = httpError(500, "{}")

        override suspend fun listarPedidos(estado: String, owner: String): Response<PedidosResponse> {
            falloRed?.let { throw it }
            listasConsultadas++
            return listas.getValue(estado)
        }

        override suspend fun obtenerPedido(id: String, owner: String): Response<PedidoCompletoResponse> {
            falloRed?.let { throw it }
            return detalles.getValue(id)
        }

        override suspend fun transicionarPedido(
            id: String,
            body: TransicionPedidoRequest,
            owner: String
        ): Response<PedidoCompletoResponse> {
            estadoEnviado = body.estado
            ownerEnviado = owner
            return transicion
        }

        override suspend fun listarPicking(owner: String): Response<PickingListaResponse> {
            falloRed?.let { throw it }
            return picking
        }

        override suspend fun obtenerPicking(id: String, owner: String) = preparaciones.getValue(id)

        override suspend fun registrarEscaneo(
            id: String,
            detalleId: String,
            body: EscaneoRequest,
            owner: String
        ): Response<EscaneoResponse> {
            falloRed?.let { throw it }
            escaneosEnviados += body
            ownerEnviado = owner
            return escaneos
        }

        override suspend fun marcarFaltante(
            id: String,
            detalleId: String,
            owner: String
        ): Response<FaltanteResponse> {
            falloRed?.let { throw it }
            ownerEnviado = owner
            return httpError(500, "{}")
        }

        override suspend fun eliminarLinea(
            id: String,
            detalleId: String,
            owner: String
        ): Response<FaltanteResponse> = httpError(500, "{}")

        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ) = httpError<com.megamarket.picker.data.remote.CandidatosSustitucionResponse>(500, "{}")

        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: com.megamarket.picker.data.remote.PropuestaSustitucionRequest,
            owner: String
        ) = httpError<com.megamarket.picker.data.remote.SustitucionResponse>(500, "{}")
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
        ) = httpError<com.megamarket.picker.data.remote.SustitucionResponse>(500, "{}")
    }

    private companion object {
        const val UUID_ESCANEO = "11111111-1111-4111-8111-111111111111"
    }

    private class AlmacenMemoria : AlmacenSesion {
        var activo: String? = "picker-1"
        override fun usuarioActivoId(): String? = activo
        override fun tokenActual(): String? = activo?.let { "token" }
        override fun tokenDe(usuarioId: String): String? = tokenActual()?.takeIf { usuarioId == activo }
        override fun perfil(): PerfilSesion? = null
        override fun guardar(accessToken: String, perfil: PerfilSesion) = Unit
        override fun limpiarSesion() { activo = null }
    }
}
