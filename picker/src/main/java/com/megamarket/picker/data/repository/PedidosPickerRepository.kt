package com.megamarket.picker.data.repository

import com.google.gson.Gson
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.LineaPickingEntity
import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity
import com.megamarket.picker.data.local.PedidoPickingEntity
import com.megamarket.picker.data.local.dao.LineaPickingDao
import com.megamarket.picker.data.local.dao.OperacionPickingDao
import com.megamarket.picker.data.local.dao.PedidoPickingDao
import com.megamarket.picker.data.remote.ApiClient
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.ErrorApiDto
import com.megamarket.picker.data.remote.EscaneoResponse
import com.megamarket.picker.data.remote.LineaPickingDto
import com.megamarket.picker.data.remote.PedidoListaDto
import com.megamarket.picker.data.remote.PickingDetalleResponse
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import com.megamarket.picker.escaneo.CANTIDAD_ESCANEO
import com.megamarket.picker.escaneo.textoCodigoBarras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class PedidosPickerRepository(
    private val api: ApiService,
    private val almacen: AlmacenSesion,
    private val baseUrl: String,
    private val pedidoDao: PedidoPickingDao,
    private val lineaDao: LineaPickingDao,
    private val operacionDao: OperacionPickingDao,
    private val sincronizacion: SincronizacionPickingRepository,
    private val programarSincronizacion: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun cargarTablero(): ResultadoOperacion<TableroPicker> = withContext(io) {
        val owner = almacen.usuarioActivoId()
            ?: return@withContext sesionVencida()
        try {
            val confirmados = listarEstado("CONFIRMADO", owner)
            if (confirmados is ResultadoOperacion.Error) return@withContext confirmados
            val pagados = listarEstado("PAGADO", owner)
            if (pagados is ResultadoOperacion.Error) return@withContext pagados
            val picking = listarAsignados(owner)
            if (picking is ResultadoOperacion.Error) return@withContext picking

            val candidatos = ((confirmados as ResultadoOperacion.Exito).valor +
                (pagados as ResultadoOperacion.Exito).valor)
                .filter { perteneceAlPicker(it.pickerId, owner) }
                .distinctBy { it.id }

            val disponibles = evaluarDisponibles(candidatos, owner)
            if (disponibles is ResultadoOperacion.Error) return@withContext disponibles

            ResultadoOperacion.Exito(
                TableroPicker(
                    disponibles = (disponibles as ResultadoOperacion.Exito).valor
                        .sortedWith(compareBy(nullsLast()) { it.fecha }),
                    asignados = (picking as ResultadoOperacion.Exito).valor
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    suspend fun tomar(pedidoId: String): ResultadoOperacion<String> = withContext(io) {
        val owner = almacen.usuarioActivoId()
            ?: return@withContext sesionVencida()
        try {
            val respuesta = api.transicionarPedido(
                id = pedidoId,
                body = TransicionPedidoRequest(estado = "EN_PICKING"),
                owner = owner
            )
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            ResultadoOperacion.Exito(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    /**
     * Quita una línea FALTANTE solo si el servidor ya tiene un rechazo trazable.
     * El stock y el ajuste económico los aplica el API; aquí no se simula la devolución.
     */
    suspend fun resolverLineaSinProducto(
        pedidoId: String,
        detalleId: String
    ): ResultadoOperacion<PreparacionPedido> = withContext(io) {
        val owner = almacen.usuarioActivoId()
            ?: return@withContext sesionVencida()
        try {
            val respuesta = api.eliminarLinea(pedidoId, detalleId, owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            obtenerPreparacion(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    /** Pasa EN_PICKING → LISTO para que el pedido entre al pool de delivery. */
    suspend fun marcarListo(pedidoId: String): ResultadoOperacion<String> = withContext(io) {
        val owner = almacen.usuarioActivoId()
            ?: return@withContext sesionVencida()
        try {
            val respuesta = api.transicionarPedido(
                id = pedidoId,
                body = TransicionPedidoRequest(estado = "LISTO"),
                owner = owner
            )
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            // Solo tras confirmación del servidor: retirar de la cola local activa.
            lineaDao.eliminarDePedido(pedidoId, owner)
            pedidoDao.eliminar(pedidoId, owner)
            ResultadoOperacion.Exito(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    suspend fun obtenerPreparacion(pedidoId: String): ResultadoOperacion<PreparacionPedido> =
        withContext(io) {
            val owner = almacen.usuarioActivoId()
                ?: return@withContext sesionVencida()
            try {
                val respuesta = api.obtenerPicking(pedidoId, owner)
                if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
                val body = respuesta.body()
                    ?: return@withContext ResultadoOperacion.Error(
                        TipoErrorPedido.SERVIDOR,
                        "No se pudo leer el detalle de preparación"
                    )
                guardarPreparacion(body, owner)
                ResultadoOperacion.Exito(
                    preparacionDesdeCache(pedidoId, owner)
                        ?: body.aPreparacion(baseUrl)
                        ?: return@withContext ResultadoOperacion.Error(
                            TipoErrorPedido.SERVIDOR,
                            "No se pudo leer el detalle de preparación"
                        )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                val local = preparacionDesdeCache(pedidoId, owner)
                if (local != null) ResultadoOperacion.Exito(local) else falloRed(error)
            }
        }

    fun observarPreparacion(pedidoId: String): Flow<PreparacionPedido?> {
        val owner = almacen.usuarioActivoId() ?: return flowOf(null)
        return combine(
            lineaDao.observarDePedido(pedidoId, owner),
            operacionDao.observarDePedido(pedidoId, owner)
        ) { lineas, ops ->
            if (lineas.isEmpty()) null
            else armarPreparacion(pedidoId, owner, lineas, ops)
        }
    }

    fun observarOperacionesDetalle(detalleId: String): Flow<List<ResumenOperacionLocal>> {
        val owner = almacen.usuarioActivoId() ?: return flowOf(emptyList())
        return operacionDao.observarDeDetalle(detalleId, owner).map { lista ->
            lista.map { it.aResumen() }
        }
    }

    suspend fun registrarEscaneo(
        pedidoId: String,
        detalleId: String,
        uuidOperacion: String,
        codigoBarras: String
    ): ResultadoEscaneo = withContext(io) {
        val owner = almacen.usuarioActivoId()
            ?: return@withContext ResultadoEscaneo.Confirmado(
                tipo = TipoResultadoEscaneo.SESION_VENCIDA,
                mensaje = MensajesSesion.EXPIRADA,
                codigo = null,
                linea = null,
                vibrar = false
            )
        val codigo = textoCodigoBarras(codigoBarras)
        if (codigo.isEmpty()) {
            return@withContext ResultadoEscaneo.Fallo(
                TipoErrorPedido.SERVIDOR,
                "codigoBarras es obligatorio",
                "CODIGO_REQUERIDO",
                conservarOperacion = false
            )
        }

        val existente = operacionDao.porUuid(uuidOperacion)
        if (existente != null) {
            if (existente.ownerId != owner) {
                return@withContext ResultadoEscaneo.Fallo(
                    TipoErrorPedido.PROHIBIDO,
                    "La operación pertenece a otra cuenta",
                    null,
                    conservarOperacion = false
                )
            }
            programarSincronizacion()
            sincronizacion.sincronizarPendientes()
            return@withContext resultadoTrasSincronizar(existente.uuidOperacion, detalleId, owner)
        }

        val linea = lineaDao.obtener(detalleId, owner)
            ?: return@withContext ResultadoEscaneo.Fallo(
                TipoErrorPedido.NO_ENCONTRADO,
                "No se encontró el producto del pedido",
                "DETALLE_NO_ENCONTRADO",
                conservarOperacion = false
            )
        val pendiente = operacionDao.cantidadPendienteEscaneos(detalleId, owner)
        if (!cabeEscaneo(linea.cantidadArmada, pendiente, linea.cantidad, CANTIDAD_ESCANEO)) {
            return@withContext ResultadoEscaneo.Confirmado(
                tipo = TipoResultadoEscaneo.CANTIDAD_EXCEDIDA,
                mensaje = "La cantidad armada no puede superar la solicitada",
                codigo = "CANTIDAD_EXCEDIDA",
                linea = linea.aPreparacion(baseUrl, pendiente, hayFaltantePendiente(operacionDao.activasDeDetalle(detalleId, owner))),
                vibrar = false,
                cantidadPendienteLocal = pendiente
            )
        }
        if (hayFaltantePendiente(operacionDao.activasDeDetalle(detalleId, owner))) {
            return@withContext ResultadoEscaneo.Fallo(
                TipoErrorPedido.CONFLICTO,
                "Hay un faltante pendiente para este producto",
                "FALTANTE_PENDIENTE",
                conservarOperacion = false
            )
        }

        val ahora = System.currentTimeMillis()
        operacionDao.insertar(
            OperacionPickingEntity(
                uuidOperacion = uuidOperacion,
                ownerId = owner,
                pedidoId = pedidoId,
                detalleId = detalleId,
                operacion = OperacionPicking.ESCANEAR,
                codigoBarras = codigo,
                cantidad = CANTIDAD_ESCANEO,
                estado = OperacionPicking.PENDIENTE,
                fechaCreacion = ahora
            )
        )
        programarSincronizacion()
        sincronizacion.sincronizarPendientes()
        resultadoTrasSincronizar(uuidOperacion, detalleId, owner)
    }

    suspend fun marcarFaltante(
        pedidoId: String,
        detalleId: String,
        uuidOperacion: String
    ): ResultadoEscaneo = withContext(io) {
        val owner = almacen.usuarioActivoId()
            ?: return@withContext ResultadoEscaneo.Confirmado(
                tipo = TipoResultadoEscaneo.SESION_VENCIDA,
                mensaje = MensajesSesion.EXPIRADA,
                codigo = null,
                linea = null,
                vibrar = false
            )
        val existente = operacionDao.porUuid(uuidOperacion)
        if (existente != null) {
            if (existente.ownerId != owner) {
                return@withContext ResultadoEscaneo.Fallo(
                    TipoErrorPedido.PROHIBIDO,
                    "La operación pertenece a otra cuenta",
                    null,
                    conservarOperacion = false
                )
            }
            programarSincronizacion()
            sincronizacion.sincronizarPendientes()
            return@withContext resultadoTrasSincronizar(existente.uuidOperacion, detalleId, owner)
        }
        val linea = lineaDao.obtener(detalleId, owner)
            ?: return@withContext ResultadoEscaneo.Fallo(
                TipoErrorPedido.NO_ENCONTRADO,
                "No se encontró el producto del pedido",
                null,
                conservarOperacion = false
            )
        if (operacionDao.faltantesActivos(detalleId, owner) > 0) {
            return@withContext resultadoLineaLocal(detalleId, owner, pendiente = true)
        }
        if (operacionDao.cantidadPendienteEscaneos(detalleId, owner) > 0) {
            return@withContext ResultadoEscaneo.Fallo(
                TipoErrorPedido.CONFLICTO,
                "Hay escaneos pendientes para este producto",
                "ESCANEOS_PENDIENTES",
                conservarOperacion = false
            )
        }
        if (linea.estadoPicking != "PENDIENTE" && linea.estadoPicking != "FALTANTE") {
            return@withContext ResultadoEscaneo.Confirmado(
                tipo = TipoResultadoEscaneo.RECHAZADO,
                mensaje = "No se puede marcar FALTANTE desde ${linea.estadoPicking}",
                codigo = "ESTADO_PICKING_INVALIDO",
                linea = linea.aPreparacion(baseUrl, 0, false),
                vibrar = false
            )
        }
        val ahora = System.currentTimeMillis()
        operacionDao.insertar(
            OperacionPickingEntity(
                uuidOperacion = uuidOperacion,
                ownerId = owner,
                pedidoId = pedidoId,
                detalleId = detalleId,
                operacion = OperacionPicking.MARCAR_FALTANTE,
                codigoBarras = null,
                cantidad = 0,
                estado = OperacionPicking.PENDIENTE,
                fechaCreacion = ahora
            )
        )
        programarSincronizacion()
        sincronizacion.sincronizarPendientes()
        resultadoTrasSincronizar(uuidOperacion, detalleId, owner)
    }

    private suspend fun resultadoTrasSincronizar(
        uuid: String,
        detalleId: String,
        owner: String
    ): ResultadoEscaneo {
        val op = operacionDao.porUuid(uuid) ?: return ResultadoEscaneo.Fallo(
            TipoErrorPedido.SERVIDOR,
            "Operación no encontrada",
            null,
            conservarOperacion = false
        )
        val linea = lineaDao.obtener(detalleId, owner)
        val ops = operacionDao.activasDeDetalle(detalleId, owner)
        val pendiente = cantidadPendienteEscaneos(ops)
        val lineaUi = linea?.aPreparacion(baseUrl, pendiente, hayFaltantePendiente(ops), ops.map { it.aResumen() })
        return when (op.estado) {
            OperacionPicking.SINCRONIZADO -> when {
                op.operacion == OperacionPicking.ESCANEAR &&
                    op.resultado == OperacionPicking.RESULTADO_INCORRECTO ->
                    ResultadoEscaneo.Confirmado(
                        tipo = TipoResultadoEscaneo.INCORRECTO,
                        mensaje = op.ultimoError ?: "Código de barras no corresponde al producto solicitado",
                        codigo = "CODIGO_INCORRECTO",
                        linea = lineaUi,
                        vibrar = true,
                        cantidadPendienteLocal = pendiente
                    )
                op.operacion == OperacionPicking.ESCANEAR ->
                    ResultadoEscaneo.Confirmado(
                        tipo = TipoResultadoEscaneo.CORRECTO,
                        mensaje = null,
                        codigo = null,
                        linea = lineaUi,
                        vibrar = false,
                        cantidadPendienteLocal = pendiente
                    )
                else -> ResultadoEscaneo.Confirmado(
                    tipo = TipoResultadoEscaneo.CORRECTO,
                    mensaje = "Producto marcado como faltante",
                    codigo = null,
                    linea = lineaUi,
                    vibrar = false
                )
            }
            OperacionPicking.ERROR -> {
                val codigo = op.resultado?.takeIf { it.isNotBlank() } ?: when {
                    op.ultimoError?.contains("superar la solicitada", true) == true -> "CANTIDAD_EXCEDIDA"
                    op.ultimoError?.contains("asignado", true) == true -> "PEDIDO_NO_ASIGNADO"
                    else -> null
                }
                val tipo = when (codigo) {
                    "CANTIDAD_EXCEDIDA" -> TipoResultadoEscaneo.CANTIDAD_EXCEDIDA
                    "PEDIDO_NO_ASIGNADO" -> TipoResultadoEscaneo.PEDIDO_NO_ASIGNADO
                    else -> TipoResultadoEscaneo.RECHAZADO
                }
                ResultadoEscaneo.Confirmado(
                    tipo = tipo,
                    mensaje = op.ultimoError,
                    codigo = codigo,
                    linea = lineaUi,
                    vibrar = false,
                    cantidadPendienteLocal = pendiente
                )
            }
            else -> {
                if (op.ultimoError == MensajesSesion.EXPIRADA ||
                    op.ultimoError?.contains("Token", true) == true ||
                    op.ultimoError?.contains("Sesión", true) == true
                ) {
                    ResultadoEscaneo.Confirmado(
                        tipo = TipoResultadoEscaneo.SESION_VENCIDA,
                        mensaje = op.ultimoError ?: MensajesSesion.EXPIRADA,
                        codigo = null,
                        linea = lineaUi,
                        vibrar = false,
                        cantidadPendienteLocal = pendiente
                    )
                } else {
                    ResultadoEscaneo.PendienteLocal(
                        mensaje = op.ultimoError ?: "Escaneo guardado. Se enviará al recuperar la conexión.",
                        cantidadArmada = linea?.cantidadArmada ?: 0,
                        cantidadPendienteLocal = pendiente,
                        estadoLinea = linea?.estadoPicking ?: "PENDIENTE"
                    )
                }
            }
        }
    }

    private suspend fun resultadoLineaLocal(
        detalleId: String,
        owner: String,
        pendiente: Boolean
    ): ResultadoEscaneo {
        val linea = lineaDao.obtener(detalleId, owner)
        val ops = operacionDao.activasDeDetalle(detalleId, owner)
        val cantidadPendiente = cantidadPendienteEscaneos(ops)
        return ResultadoEscaneo.PendienteLocal(
            mensaje = if (pendiente) "Faltante pendiente de sincronizar" else null,
            cantidadArmada = linea?.cantidadArmada ?: 0,
            cantidadPendienteLocal = cantidadPendiente,
            estadoLinea = linea?.estadoPicking ?: "PENDIENTE"
        )
    }

    private suspend fun guardarPreparacion(body: PickingDetalleResponse, owner: String) {
        val pedidoId = body.pedido?.id?.trim().orEmpty()
        if (pedidoId.isEmpty()) return
        val ahora = System.currentTimeMillis()
        pedidoDao.guardar(
            PedidoPickingEntity(
                id = pedidoId,
                ownerId = owner,
                totalCentimos = body.pedido?.totalCentimos ?: 0L,
                fecha = body.pedido?.fecha,
                modalidadPago = body.pago?.modalidad,
                estadoPago = body.pago?.estado,
                actualizadoEn = ahora
            )
        )
        val lineas = body.detalles.orEmpty().mapNotNull { dto ->
            val id = dto.id?.trim().orEmpty()
            val producto = dto.productoId?.trim().orEmpty()
            if (id.isEmpty() || producto.isEmpty()) null
            else LineaPickingEntity(
                id = id,
                pedidoId = pedidoId,
                ownerId = owner,
                productoId = producto,
                nombre = dto.nombreProducto?.trim().orEmpty().ifBlank { "Producto" },
                cantidad = dto.cantidad ?: 0,
                cantidadArmada = dto.cantidadArmada ?: 0,
                estadoPicking = dto.estadoPicking?.trim().orEmpty().ifBlank { "PENDIENTE" },
                pasillo = dto.pasillo,
                codigoBarras = dto.codigoBarras?.trim()?.ifBlank { null },
                actualizadoEn = ahora,
                sustitucionAceptadaId = dto.sustitucionAceptadaId?.trim()?.ifBlank { null },
                productoSustitutoId = dto.productoSustitutoId?.trim()?.ifBlank { null },
                nombreSustituto = dto.nombreSustituto?.trim()?.ifBlank { null },
                resolubleSinProducto = dto.resolubleSinProducto == true
            )
        }
        lineaDao.eliminarDePedido(pedidoId, owner)
        if (lineas.isNotEmpty()) lineaDao.guardarTodas(lineas)
    }

    private suspend fun preparacionDesdeCache(pedidoId: String, owner: String): PreparacionPedido? {
        val pedido = pedidoDao.obtener(pedidoId, owner) ?: return null
        val lineas = lineaDao.dePedido(pedidoId, owner)
        if (lineas.isEmpty()) return null
        val opsPorDetalle = lineas.associate { linea ->
            linea.id to operacionDao.deDetalle(linea.id, owner)
        }
        return PreparacionPedido(
            id = pedido.id,
            totalCentimos = pedido.totalCentimos,
            fecha = pedido.fecha,
            modalidadPago = pedido.modalidadPago,
            estadoPago = pedido.estadoPago,
            grupos = agruparPorPasillo(
                lineas.map { linea ->
                    val ops = opsPorDetalle[linea.id].orEmpty()
                    linea.aPreparacion(
                        baseUrl = baseUrl,
                        pendiente = cantidadPendienteEscaneos(ops),
                        faltantePendiente = hayFaltantePendiente(ops),
                        operaciones = ops.map { it.aResumen() }
                    )
                }
            )
        )
    }

    private suspend fun armarPreparacion(
        pedidoId: String,
        owner: String,
        lineas: List<LineaPickingEntity>,
        ops: List<OperacionPickingEntity>
    ): PreparacionPedido {
        val pedido = pedidoDao.obtener(pedidoId, owner)
        val porDetalle = ops.groupBy { it.detalleId }
        return PreparacionPedido(
            id = pedidoId,
            totalCentimos = pedido?.totalCentimos ?: 0L,
            fecha = pedido?.fecha,
            modalidadPago = pedido?.modalidadPago,
            estadoPago = pedido?.estadoPago,
            grupos = agruparPorPasillo(
                lineas.map { linea ->
                    val detalleOps = porDetalle[linea.id].orEmpty()
                    linea.aPreparacion(
                        baseUrl = baseUrl,
                        pendiente = cantidadPendienteEscaneos(detalleOps),
                        faltantePendiente = hayFaltantePendiente(detalleOps),
                        operaciones = detalleOps.map { it.aResumen() }
                    )
                }
            )
        )
    }

    private suspend fun evaluarDisponibles(
        candidatos: List<PedidoListaDto>,
        owner: String
    ): ResultadoOperacion<List<PedidoDisponible>> = coroutineScope {
        val resultados = candidatos.map { pedido ->
            async {
                when (pedido.estado) {
                    "PAGADO" -> ResultadoOperacion.Exito(pedido.aDisponible(modalidad = null))
                    "CONFIRMADO" -> consultarConfirmado(pedido, owner)
                    else -> ResultadoOperacion.Exito(null)
                }
            }
        }.awaitAll()
        val error = resultados.firstOrNull { it is ResultadoOperacion.Error } as? ResultadoOperacion.Error
        if (error != null) return@coroutineScope error
        ResultadoOperacion.Exito(
            resultados.mapNotNull { (it as ResultadoOperacion.Exito).valor }
        )
    }

    private suspend fun consultarConfirmado(
        pedido: PedidoListaDto,
        owner: String
    ): ResultadoOperacion<PedidoDisponible?> {
        val id = pedido.id ?: return ResultadoOperacion.Exito(null)
        val respuesta = api.obtenerPedido(id, owner)
        if (respuesta.code() == 403 || respuesta.code() == 404) {
            return ResultadoOperacion.Exito(null)
        }
        if (!respuesta.isSuccessful) return fallo(respuesta)
        val pago = respuesta.body()?.pago
        val consulta = if (pago == null) null else PagoDtoConsulta(pago.modalidad)
        if (!puedePasarAEnPicking("CONFIRMADO", consulta)) {
            return ResultadoOperacion.Exito(null)
        }
        return ResultadoOperacion.Exito(pedido.aDisponible(consulta?.modalidad))
    }

    private suspend fun listarEstado(
        estado: String,
        owner: String
    ): ResultadoOperacion<List<PedidoListaDto>> {
        val respuesta = api.listarPedidos(estado, owner)
        if (!respuesta.isSuccessful) return fallo(respuesta)
        return ResultadoOperacion.Exito(respuesta.body()?.pedidos.orEmpty())
    }

    private suspend fun listarAsignados(owner: String): ResultadoOperacion<List<PedidoAsignado>> {
        val respuesta = api.listarPicking(owner)
        if (!respuesta.isSuccessful) return fallo(respuesta)
        val asignados = respuesta.body()?.pedidos.orEmpty().mapNotNull { item ->
            val pedido = item.pedido ?: return@mapNotNull null
            val id = pedido.id?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            PedidoAsignado(
                id = id,
                totalCentimos = pedido.totalCentimos ?: 0L,
                fecha = pedido.fecha,
                cantidadLineas = item.detalles?.size ?: 0
            )
        }
        return ResultadoOperacion.Exito(asignados)
    }

    private fun <T> fallo(response: Response<*>): ResultadoOperacion<T> {
        val (mensaje, codigo) = leerError(response)
        val tipo = when (response.code()) {
            401 -> TipoErrorPedido.SESION
            403 -> TipoErrorPedido.PROHIBIDO
            404 -> TipoErrorPedido.NO_ENCONTRADO
            409 -> TipoErrorPedido.CONFLICTO
            else -> TipoErrorPedido.SERVIDOR
        }
        val texto = if (tipo == TipoErrorPedido.SESION && mensaje.isBlank()) {
            MensajesSesion.EXPIRADA
        } else {
            mensaje
        }
        return ResultadoOperacion.Error(tipo, texto, codigo)
    }

    private fun leerError(response: Response<*>): Pair<String, String?> {
        val crudo = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val dto = runCatching { gson.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()
        val mensaje = dto?.error?.takeIf { it.isNotBlank() }
            ?: mensajePorHttp(response.code())
        return mensaje to dto?.codigo
    }

    private fun mensajePorHttp(codigo: Int): String = when (codigo) {
        401 -> MensajesSesion.EXPIRADA
        403 -> "No tienes permiso sobre este pedido"
        404 -> "Pedido no encontrado"
        409 -> "No se pudo tomar el pedido"
        in 500..599 -> MensajesSesion.SERVIDOR
        else -> "No se pudo completar la operación ($codigo)"
    }

    private fun <T> falloRed(error: IOException): ResultadoOperacion<T> {
        if (error.message == ApiClient.MENSAJE_CUENTA_CAMBIADA) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.CUENTA,
                ApiClient.MENSAJE_CUENTA_CAMBIADA
            )
        }
        return ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
    }

    private fun <T> sesionVencida(): ResultadoOperacion<T> =
        ResultadoOperacion.Error(TipoErrorPedido.SESION, MensajesSesion.EXPIRADA)
}

private fun perteneceAlPicker(pickerId: String?, owner: String): Boolean {
    val asignado = pickerId?.trim().orEmpty()
    return asignado.isEmpty() || asignado == owner
}

private fun PedidoListaDto.aDisponible(modalidad: String?): PedidoDisponible? {
    val identificador = id?.trim().orEmpty()
    if (identificador.isEmpty()) return null
    return PedidoDisponible(
        id = identificador,
        estado = estado?.trim().orEmpty(),
        totalCentimos = totalCentimos ?: 0L,
        fecha = fecha,
        modalidad = modalidad
    )
}

private fun LineaPickingEntity.aPreparacion(
    baseUrl: String,
    pendiente: Int,
    faltantePendiente: Boolean,
    operaciones: List<ResumenOperacionLocal> = emptyList()
): LineaPreparacion {
    val linea = LineaPreparacion(
        id = id,
        productoId = productoId,
        nombre = nombre,
        cantidad = cantidad,
        cantidadArmada = cantidadArmada,
        estadoPicking = estadoPicking,
        pasillo = pasillo,
        codigoBarras = codigoBarras,
        imagenUrl = null,
        cantidadPendienteLocal = pendiente,
        faltantePendiente = faltantePendiente,
        operacionesLocales = operaciones,
        sustitucionAceptadaId = sustitucionAceptadaId,
        productoSustitutoId = productoSustitutoId,
        nombreSustituto = nombreSustituto,
        resolubleSinProducto = resolubleSinProducto
    )
    return linea.copy(imagenUrl = urlImagenProducto(baseUrl, linea.productoImagenId()))
}

private fun PickingDetalleResponse.aPreparacion(baseUrl: String): PreparacionPedido? {
    val identificador = pedido?.id?.trim().orEmpty()
    if (identificador.isEmpty()) return null
    val lineas = detalles.orEmpty().mapNotNull { it.aLinea(baseUrl) }
    return PreparacionPedido(
        id = identificador,
        totalCentimos = pedido?.totalCentimos ?: 0L,
        fecha = pedido?.fecha,
        modalidadPago = pago?.modalidad,
        estadoPago = pago?.estado,
        grupos = agruparPorPasillo(lineas)
    )
}

internal fun LineaPickingDto.aLinea(baseUrl: String): LineaPreparacion? {
    val identificador = id?.trim().orEmpty()
    val producto = productoId?.trim().orEmpty()
    if (identificador.isEmpty() || producto.isEmpty()) return null
    val linea = LineaPreparacion(
        id = identificador,
        productoId = producto,
        nombre = nombreProducto?.trim().orEmpty().ifBlank { "Producto" },
        cantidad = cantidad ?: 0,
        cantidadArmada = cantidadArmada ?: 0,
        estadoPicking = estadoPicking?.trim().orEmpty().ifBlank { "PENDIENTE" },
        pasillo = pasillo,
        codigoBarras = codigoBarras?.trim()?.ifBlank { null },
        imagenUrl = null,
        sustitucionAceptadaId = sustitucionAceptadaId?.trim()?.ifBlank { null },
        productoSustitutoId = productoSustitutoId?.trim()?.ifBlank { null },
        nombreSustituto = nombreSustituto?.trim()?.ifBlank { null },
        resolubleSinProducto = resolubleSinProducto == true
    )
    return linea.copy(imagenUrl = urlImagenProducto(baseUrl, linea.productoImagenId()))
}

internal fun interpretarEscaneo(
    http: Int,
    cuerpo: EscaneoResponse?,
    baseUrl: String
): ResultadoEscaneo {
    val codigo = cuerpo?.codigo?.takeIf { it.isNotBlank() }
    val resultado = cuerpo?.escaneo?.resultado?.takeIf { it.isNotBlank() }
    val linea = cuerpo?.detalle?.aLinea(baseUrl)
    val mensaje = cuerpo?.error?.takeIf { it.isNotBlank() }

    if (http == 401) {
        return confirmado(
            TipoResultadoEscaneo.SESION_VENCIDA,
            mensaje ?: MensajesSesion.EXPIRADA,
            codigo,
            linea
        )
    }
    if (codigo == "CODIGO_INCORRECTO" || resultado == "INCORRECTO") {
        return ResultadoEscaneo.Confirmado(
            tipo = TipoResultadoEscaneo.INCORRECTO,
            mensaje = mensaje,
            codigo = codigo,
            linea = linea,
            vibrar = codigo == "CODIGO_INCORRECTO"
        )
    }
    if (codigo == "CANTIDAD_EXCEDIDA") {
        return confirmado(TipoResultadoEscaneo.CANTIDAD_EXCEDIDA, mensaje, codigo, linea)
    }
    if (codigo == "PEDIDO_NO_ASIGNADO") {
        return confirmado(TipoResultadoEscaneo.PEDIDO_NO_ASIGNADO, mensaje, codigo, linea)
    }
    if ((http == 200 || http == 201) && resultado == "CORRECTO") {
        return confirmado(TipoResultadoEscaneo.CORRECTO, mensaje, codigo, linea)
    }
    if (http == 200 || http == 201) {
        return ResultadoEscaneo.Fallo(
            tipo = TipoErrorPedido.SERVIDOR,
            mensaje = mensaje ?: "No se pudo leer la respuesta del escaneo",
            codigo = codigo,
            conservarOperacion = true
        )
    }
    val tipo = when (http) {
        403 -> TipoErrorPedido.PROHIBIDO
        404 -> TipoErrorPedido.NO_ENCONTRADO
        409 -> TipoErrorPedido.CONFLICTO
        else -> TipoErrorPedido.SERVIDOR
    }
    return ResultadoEscaneo.Fallo(
        tipo = tipo,
        mensaje = mensaje ?: mensajeEscaneo(http),
        codigo = codigo,
        conservarOperacion = http in 500..599
    )
}

private fun confirmado(
    tipo: TipoResultadoEscaneo,
    mensaje: String?,
    codigo: String?,
    linea: LineaPreparacion?
) = ResultadoEscaneo.Confirmado(
    tipo = tipo,
    mensaje = mensaje,
    codigo = codigo,
    linea = linea,
    vibrar = false
)

private fun mensajeEscaneo(http: Int): String = when (http) {
    401 -> MensajesSesion.EXPIRADA
    403 -> "El pedido no está asignado a este picker"
    404 -> "No se encontró el producto del pedido"
    409 -> "No se pudo registrar el escaneo"
    in 500..599 -> MensajesSesion.SERVIDOR
    else -> "No se pudo registrar el escaneo ($http)"
}
