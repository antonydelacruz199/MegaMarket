package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.LineaDaoMemoria
import com.megamarket.picker.data.local.LineaPickingEntity
import com.megamarket.picker.data.local.OperacionDaoMemoria
import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity
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
import com.megamarket.picker.data.remote.PedidoCompletoResponse
import com.megamarket.picker.data.remote.PedidosResponse
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.PickingListaResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class SincronizacionPickingRepositoryTest {

    @Test
    fun sinSesionNoTocaOperacionesAjenasNiPropias() = runBlocking {
        val ops = OperacionDaoMemoria()
        ops.insertar(op(owner = "picker-1"))
        val sync = sync(ApiFalsa(), ops, Almacen(null))
        assertTrue(sync.sincronizarPendientes() is ResultadoSincronizacion.SinTrabajo)
        assertEquals(OperacionPicking.PENDIENTE, ops.porUuid(UUID)?.estado)
    }

    @Test
    fun operacionesDeOtroUsuarioPermanecenPendientes() = runBlocking {
        val api = ApiFalsa()
        val ops = OperacionDaoMemoria()
        ops.insertar(op(owner = "picker-a"))
        val sync = sync(api, ops, Almacen("picker-b"))
        sync.sincronizarPendientes()
        assertTrue(api.envios.isEmpty())
        assertEquals(OperacionPicking.PENDIENTE, ops.porUuid(UUID)?.estado)
    }

    @Test
    fun error401DevuelveReintentarYConservaPendiente() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.error(
            401,
            """{"error":"Token inválido o expirado"}""".toResponseBody(JSON)
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op())
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        val resultado = sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertTrue(resultado is ResultadoSincronizacion.Reintentar)
        assertEquals(OperacionPicking.PENDIENTE, ops.porUuid(UUID)?.estado)
    }

    @Test
    fun conflictoDefinitivoMarcaError() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.error(
            409,
            """{"error":"La cantidad armada no puede superar la solicitada","codigo":"CANTIDAD_EXCEDIDA"}"""
                .toResponseBody(JSON)
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op())
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertEquals(OperacionPicking.ERROR, ops.porUuid(UUID)?.estado)
    }

    @Test
    fun ejecucionesConcurrentesNoDuplicanEnvios() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.success(
            EscaneoResponse(
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "770001", cantidad = 1),
                detalle = LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 2,
                    cantidadArmada = 1,
                    estadoPicking = "PENDIENTE",
                    precioUnitarioCentimos = 1,
                    subtotalCentimos = 2,
                    pasillo = "1",
                    codigoBarras = "770001",
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            )
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op())
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        val sync = sync(api, ops, Almacen("picker-1"), lineas)
        listOf(
            async { sync.sincronizarPendientes() },
            async { sync.sincronizarPendientes() },
            async { sync.sincronizarPendientes() }
        ).awaitAll()
        assertEquals(1, api.envios.size)
        assertEquals(OperacionPicking.SINCRONIZADO, ops.porUuid(UUID)?.estado)
        assertEquals(1, lineas.obtener("d1", "picker-1")?.cantidadArmada)
    }

    @Test
    fun enviandoAtascadoSeRecuperaYSeReenviaConElMismoUuid() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.success(
            EscaneoResponse(
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "770001", cantidad = 1),
                detalle = LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 2,
                    cantidadArmada = 1,
                    estadoPicking = "PENDIENTE",
                    precioUnitarioCentimos = 1,
                    subtotalCentimos = 2,
                    pasillo = "1",
                    codigoBarras = "770001",
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            )
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op().copy(estado = OperacionPicking.ENVIANDO))
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        val resultado = sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertTrue(resultado is ResultadoSincronizacion.Exito)
        assertEquals(1, api.envios.size)
        assertEquals(UUID, api.envios.single().uuidOperacion)
        assertEquals(OperacionPicking.SINCRONIZADO, ops.porUuid(UUID)?.estado)
    }

    @Test
    fun escaneosYFaltanteDeLaMismaLineaSeProcesanEnOrden() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.success(
            EscaneoResponse(
                escaneo = EscaneoDto(resultado = "CORRECTO", codigoBarras = "770001", cantidad = 1),
                detalle = LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 2,
                    cantidadArmada = 1,
                    estadoPicking = "PENDIENTE",
                    precioUnitarioCentimos = 1,
                    subtotalCentimos = 2,
                    pasillo = "1",
                    codigoBarras = "770001",
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            )
        )
        api.faltante = Response.success(
            FaltanteResponse(
                detalle = LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 2,
                    cantidadArmada = 1,
                    estadoPicking = "FALTANTE",
                    precioUnitarioCentimos = 1,
                    subtotalCentimos = 2,
                    pasillo = "1",
                    codigoBarras = "770001",
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            )
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op().copy(uuidOperacion = "11111111-1111-4111-8111-111111111101", fechaCreacion = 1L))
        ops.insertar(
            op().copy(
                uuidOperacion = "11111111-1111-4111-8111-111111111102",
                operacion = OperacionPicking.MARCAR_FALTANTE,
                codigoBarras = null,
                cantidad = 0,
                fechaCreacion = 2L
            )
        )
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertEquals(listOf("ESCANEAR", "FALTANTE"), api.orden)
        assertEquals(OperacionPicking.SINCRONIZADO, ops.porUuid("11111111-1111-4111-8111-111111111101")?.estado)
        assertEquals(OperacionPicking.SINCRONIZADO, ops.porUuid("11111111-1111-4111-8111-111111111102")?.estado)
    }

    @Test
    fun conflicto409ConservaCodigoYNoSeReintenta() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.error(
            409,
            """{"error":"La cantidad armada no puede superar la solicitada","codigo":"CANTIDAD_EXCEDIDA"}"""
                .toResponseBody(JSON)
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op())
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        val guardada = ops.porUuid(UUID)!!
        assertEquals(OperacionPicking.ERROR, guardada.estado)
        assertEquals("CANTIDAD_EXCEDIDA", guardada.resultado)
        api.envios.clear()
        val segunda = sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertTrue(segunda is ResultadoSincronizacion.SinTrabajo)
        assertTrue(api.envios.isEmpty())
    }

    @Test
    fun http403EsDefinitivoYNoSeReenvia() = runBlocking {
        val api = ApiFalsa()
        api.respuesta = Response.error(
            403,
            """{"error":"El pedido no está asignado a este picker","codigo":"PEDIDO_NO_ASIGNADO"}"""
                .toResponseBody(JSON)
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(op())
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertEquals(OperacionPicking.ERROR, ops.porUuid(UUID)?.estado)
        assertEquals("PEDIDO_NO_ASIGNADO", ops.porUuid(UUID)?.resultado)
        api.envios.clear()
        sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertTrue(api.envios.isEmpty())
    }

    @Test
    fun faltanteOfflineSeConfirmaAlSincronizar() = runBlocking {
        val api = ApiFalsa()
        api.faltante = Response.success(
            FaltanteResponse(
                detalle = LineaPickingDto(
                    id = "d1",
                    pedidoId = "p1",
                    productoId = "prod-1",
                    nombreProducto = "Leche",
                    cantidad = 2,
                    cantidadArmada = 0,
                    estadoPicking = "FALTANTE",
                    precioUnitarioCentimos = 1,
                    subtotalCentimos = 2,
                    pasillo = "1",
                    codigoBarras = "770001",
                    fechaEscaneo = null,
                    escaneadoPor = null
                )
            )
        )
        val ops = OperacionDaoMemoria()
        ops.insertar(
            op().copy(
                operacion = OperacionPicking.MARCAR_FALTANTE,
                codigoBarras = null,
                cantidad = 0
            )
        )
        val lineas = LineaDaoMemoria()
        sembrar(lineas)
        sync(api, ops, Almacen("picker-1"), lineas).sincronizarPendientes()
        assertEquals(OperacionPicking.SINCRONIZADO, ops.porUuid(UUID)?.estado)
        assertEquals("FALTANTE", lineas.obtener("d1", "picker-1")?.estadoPicking)
        assertEquals(1, api.faltantes.get())
    }

    private fun sync(
        api: ApiService,
        ops: OperacionDaoMemoria,
        almacen: AlmacenSesion,
        lineas: LineaDaoMemoria = LineaDaoMemoria()
    ) = SincronizacionPickingRepository(api, ops, lineas, almacen, "http://10.0.2.2:8010/")

    private fun op(owner: String = "picker-1") = OperacionPickingEntity(
        uuidOperacion = UUID,
        ownerId = owner,
        pedidoId = "p1",
        detalleId = "d1",
        operacion = OperacionPicking.ESCANEAR,
        codigoBarras = "770001",
        cantidad = 1,
        estado = OperacionPicking.PENDIENTE,
        fechaCreacion = 1L
    )

    private suspend fun sembrar(lineas: LineaDaoMemoria) {
        lineas.guardarTodas(
            listOf(
                LineaPickingEntity(
                    id = "d1",
                    pedidoId = "p1",
                    ownerId = "picker-1",
                    productoId = "prod-1",
                    nombre = "Leche",
                    cantidad = 2,
                    cantidadArmada = 0,
                    estadoPicking = "PENDIENTE",
                    pasillo = "1",
                    codigoBarras = "770001",
                    actualizadoEn = 1L
                )
            )
        )
    }

    private class ApiFalsa : ApiService {

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
        val orden = mutableListOf<String>()
        val faltantes = AtomicInteger(0)
        var respuesta: Response<EscaneoResponse> = Response.error(500, "{}".toResponseBody(JSON))
        var faltante: Response<FaltanteResponse> = Response.error(500, "{}".toResponseBody(JSON))
        var fallo: IOException? = null

        override suspend fun registrarEscaneo(
            id: String,
            detalleId: String,
            body: EscaneoRequest,
            owner: String
        ): Response<EscaneoResponse> {
            fallo?.let { throw it }
            envios += body
            orden += "ESCANEAR"
            return respuesta
        }

        override suspend fun marcarFaltante(
            id: String,
            detalleId: String,
            owner: String
        ): Response<FaltanteResponse> {
            faltantes.incrementAndGet()
            orden += "FALTANTE"
            return faltante
        }

        override suspend fun eliminarLinea(
            id: String,
            detalleId: String,
            owner: String
        ): Response<FaltanteResponse> = err<FaltanteResponse>()

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
        override suspend fun obtenerPicking(id: String, owner: String) = err<PickingDetalleResponse>()
        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ) = err<com.megamarket.picker.data.remote.CandidatosSustitucionResponse>()
        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: com.megamarket.picker.data.remote.PropuestaSustitucionRequest,
            owner: String
        ) = err<com.megamarket.picker.data.remote.SustitucionResponse>()

        override suspend fun avisarSinStock(
            id: String,
            detalleId: String,
            owner: String
        ) = err<com.megamarket.picker.data.remote.SustitucionResponse>()

        override suspend fun listarEntregas(owner: String, estado: String?, page: Int?, limit: Int?) =
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
        ) = err<com.megamarket.picker.data.remote.SustitucionResponse>()

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
        const val UUID = "11111111-1111-4111-8111-111111111111"
        val JSON = "application/json".toMediaType()
    }
}
