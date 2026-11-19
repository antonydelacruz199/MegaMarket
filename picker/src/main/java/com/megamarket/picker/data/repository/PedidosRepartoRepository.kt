package com.megamarket.picker.data.repository

import com.google.gson.Gson
import com.megamarket.picker.data.local.AlmacenOperacionEntrega
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.MemoriaOperacionEntrega
import com.megamarket.picker.data.remote.ApiClient
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.DireccionEntregaDto
import com.megamarket.picker.data.remote.EntregaDetalleResponse
import com.megamarket.picker.data.remote.EntregaDto
import com.megamarket.picker.data.remote.EntregaListaItemDto
import com.megamarket.picker.data.remote.ConfirmarPagoRequest
import com.megamarket.picker.data.remote.ErrorApiDto
import com.megamarket.picker.data.remote.GenerarQrRequest
import com.megamarket.picker.data.remote.PagoDto
import com.megamarket.picker.data.remote.PedidoEntregaDto
import com.megamarket.picker.data.remote.RegistrarEntregaRequest
import com.megamarket.picker.data.remote.TransicionPedidoRequest
import com.megamarket.picker.model.RolOperativo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class PedidosRepartoRepository(
    private val api: ApiService,
    private val almacen: AlmacenSesion,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val operaciones: AlmacenOperacionEntrega = MemoriaOperacionEntrega(),
    private val compresor: CompresorFoto = CompresorFoto { origen, limite ->
        origen.takeIf { it.isNotEmpty() && it.size <= limite }
    },
    private val nuevoUuid: () -> String = { UUID.randomUUID().toString() },
    private val relojIso: () -> String = { isoAhora() }
) {
    private val gson = Gson()

    fun fotoPublicada(pedidoId: String): Boolean {
        val clave = ambito(pedidoId) ?: return false
        return fotoReferenciaDelPedido(operaciones.fotoDe(clave), pedidoId)
    }

    suspend fun cargarTablero(): ResultadoOperacion<TableroReparto> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        try {
            val respuesta = api.listarEntregas(owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val items = respuesta.body()?.pedidos.orEmpty()
            ResultadoOperacion.Exito(tableroDesde(items))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    suspend fun tomar(pedidoId: String): ResultadoOperacion<String> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        try {
            val respuesta = api.transicionarPedido(
                id = pedidoId,
                body = TransicionPedidoRequest(estado = "EN_CAMINO"),
                owner = owner
            )
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val estado = respuesta.body()?.pedido?.estado?.trim().orEmpty()
            if (!estado.equals("EN_CAMINO", ignoreCase = true)) {
                return@withContext ResultadoOperacion.Error(
                    TipoErrorPedido.SERVIDOR,
                    MensajesEntrega.CAMINO_NO_CONFIRMADO,
                    "ESTADO_NO_CONFIRMADO"
                )
            }
            ResultadoOperacion.Exito(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    suspend fun obtenerDetalle(pedidoId: String): ResultadoOperacion<DetalleReparto> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        try {
            val respuesta = api.obtenerEntrega(pedidoId, owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val detalle = detalleDesde(respuesta.body())
                ?: return@withContext ResultadoOperacion.Error(
                    TipoErrorPedido.SERVIDOR,
                    "El servidor no devolvió el pedido",
                    "RESPUESTA_INVALIDA"
                )
            ResultadoOperacion.Exito(detalle)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    /** Genera el QR simulado de contraentrega. El QR es informativo: no confirma el pago. */
    suspend fun generarQrPago(pedidoId: String): ResultadoOperacion<PagoQrReparto> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        try {
            val respuesta = api.generarQr(GenerarQrRequest(pedidoId.trim()), owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val cuerpo = respuesta.body()
            val pago = cuerpo?.pago
            val id = pago?.id?.trim().orEmpty()
            val estado = pago?.estado?.trim().orEmpty()
            if (id.isEmpty() || estado.isEmpty()) {
                return@withContext ResultadoOperacion.Error(
                    TipoErrorPedido.SERVIDOR,
                    MensajesEntrega.PAGO_RESPUESTA_INVALIDA,
                    "RESPUESTA_INVALIDA"
                )
            }
            val qr = cuerpo?.qr
            ResultadoOperacion.Exito(
                PagoQrReparto(
                    pagoId = id,
                    estadoPago = estado.uppercase(),
                    montoCentimos = qr?.importeCentimos ?: pago?.montoCentimos ?: 0L,
                    referencia = qr?.referencia ?: pago?.referenciaBanco,
                    qrContenido = qr?.contenido?.takeIf { it.isNotBlank() }
                        ?: pago?.qrContenido?.takeIf { it.isNotBlank() },
                    generadoEn = qr?.generadoEn,
                    expiraEn = qr?.expiraEn ?: pago?.fechaExpiracionQr,
                    etiqueta = qr?.etiqueta?.takeIf { it.isNotBlank() } ?: ETIQUETA_SIMULACION,
                    aviso = qr?.aviso
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    /**
     * Confirma el pago simulado. Solo devuelve éxito si el cuerpo del servidor declara CONFIRMADO;
     * [uuidOperacion] debe reutilizarse en los reintentos para mantener la idempotencia.
     */
    suspend fun confirmarPago(
        pagoId: String,
        uuidOperacion: String
    ): ResultadoOperacion<ConfirmacionPagoReparto> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        try {
            val respuesta = api.confirmarPago(pagoId.trim(), ConfirmarPagoRequest(uuidOperacion), owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val cuerpo = respuesta.body()
            val estado = cuerpo?.pago?.estado?.trim().orEmpty()
            if (!estado.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true)) {
                return@withContext ResultadoOperacion.Error(
                    TipoErrorPedido.SERVIDOR,
                    MensajesEntrega.PAGO_NO_CONFIRMADO,
                    "PAGO_NO_CONFIRMADO"
                )
            }
            ResultadoOperacion.Exito(
                ConfirmacionPagoReparto(
                    estadoPago = estado.uppercase(),
                    numeroOperacion = cuerpo?.pago?.numeroOperacionBanco,
                    estadoPedido = cuerpo?.pedido?.estado?.trim()?.uppercase(),
                    idempotente = cuerpo?.idempotente == true
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    /** Lee el estado actual del pago del pedido directamente del servidor. */
    suspend fun consultarPago(pedidoId: String): ResultadoOperacion<PagoReparto> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        try {
            val respuesta = api.obtenerPagoPedido(pedidoId.trim(), owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val pago = pagoDesde(
                respuesta.body()?.pago?.let {
                    PagoDto(
                        id = it.id,
                        modalidad = it.modalidad,
                        estado = it.estado,
                        montoCentimos = it.montoCentimos
                    )
                }
            ) ?: return@withContext ResultadoOperacion.Error(
                TipoErrorPedido.SERVIDOR,
                MensajesEntrega.PAGO_RESPUESTA_INVALIDA,
                "RESPUESTA_INVALIDA"
            )
            ResultadoOperacion.Exito(pago)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    /**
     * Sube la foto y registra la evidencia. El estado ENTREGADO solo se acepta
     * si el cuerpo de respuesta del servidor lo confirma.
     */
    suspend fun registrar(
        pedidoId: String,
        foto: ByteArray?,
        latitud: Double,
        longitud: Double,
        estadoPedido: String,
        estadoPago: String?,
        nuevaFoto: Boolean
    ): ResultadoOperacion<ResultadoRegistro> = withContext(io) {
        exigirRepartidor()?.let { return@withContext it }
        if (!gpsValido(latitud, longitud)) {
            return@withContext ResultadoOperacion.Error(
                TipoErrorPedido.SERVIDOR,
                MensajesEntrega.GPS,
                "GPS_INVALIDO"
            )
        }
        if (pagoEstaPendiente(estadoPago)) {
            return@withContext ResultadoOperacion.Error(
                TipoErrorPedido.CONFLICTO,
                MensajesEntrega.PAGO,
                CODIGO_REQUIERE_PAGO
            )
        }
        if (!estadoPedido.equals("EN_CAMINO", ignoreCase = true)) {
            return@withContext ResultadoOperacion.Error(
                TipoErrorPedido.CONFLICTO,
                MensajesEntrega.SOLO_EN_CAMINO,
                "PEDIDO_NO_EN_CAMINO"
            )
        }
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        val ambito = ambito(pedidoId) ?: return@withContext sesionVencida()
        val uuid = uuidVigente(ambito)
        val publicada = when (val clave = publicarFoto(pedidoId, ambito, foto, owner, nuevaFoto)) {
            is ResultadoOperacion.Exito -> clave.valor
            is ResultadoOperacion.Error -> return@withContext clave
        }
        try {
            val respuesta = api.registrarEntrega(
                id = pedidoId,
                body = RegistrarEntregaRequest(
                    uuidOperacion = uuid,
                    fotoReferencia = publicada,
                    latitud = latitud,
                    longitud = longitud,
                    fechaEntrega = relojIso()
                ),
                owner = owner
            )
            if (!respuesta.isSuccessful) {
                return@withContext conFotoPublicada(fallo(respuesta))
            }
            val cuerpo = respuesta.body()
            val estado = cuerpo?.pedido?.estado?.trim().orEmpty()
            if (!estado.equals("ENTREGADO", ignoreCase = true)) {
                return@withContext conFotoPublicada(
                    ResultadoOperacion.Error(
                        TipoErrorPedido.SERVIDOR,
                        MensajesEntrega.NO_CONFIRMADO,
                        "ESTADO_NO_CONFIRMADO"
                    )
                )
            }
            operaciones.limpiar(ambito)
            ResultadoOperacion.Exito(
                ResultadoRegistro(
                    estadoPedido = estado,
                    idempotente = cuerpo?.idempotente == true,
                    fotoReferencia = cuerpo?.entrega?.fotoReferencia?.trim().orEmpty().ifBlank { publicada },
                    uuidOperacion = cuerpo?.entrega?.uuidOperacion?.trim().orEmpty().ifBlank { uuid }
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            conFotoPublicada(falloRed(error))
        }
    }

    private suspend fun publicarFoto(
        pedidoId: String,
        ambito: String,
        origen: ByteArray?,
        owner: String,
        nuevaFoto: Boolean
    ): ResultadoOperacion<String> {
        val previa = operaciones.fotoDe(ambito)?.trim()
        if (!nuevaFoto && fotoReferenciaDelPedido(previa, pedidoId)) {
            return ResultadoOperacion.Exito(previa!!)
        }
        if (origen == null || origen.isEmpty()) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.SERVIDOR,
                MensajesEntrega.FOTO,
                "FOTO_REQUERIDA"
            )
        }
        var limite = LIMITE_FOTO_BYTES
        var preparado = compresor.jpeg(origen, limite)
            ?: return ResultadoOperacion.Error(
                TipoErrorPedido.SERVIDOR,
                MensajesEntrega.FOTO_COMPRESION,
                "FOTO_INVALIDA"
            )
        var ultimo: ResultadoOperacion<String> = ResultadoOperacion.Error(
            TipoErrorPedido.SERVIDOR,
            MensajesEntrega.FOTO_COMPRESION,
            "FOTO_INVALIDA"
        )
        repeat(2) { paso ->
            try {
                val parte = MultipartBody.Part.createFormData(
                    "archivo",
                    "foto.jpg",
                    preparado.toRequestBody("image/jpeg".toMediaType())
                )
                val respuesta = api.subirFotoEntrega(pedidoId, parte, owner)
                if (respuesta.isSuccessful) {
                    val clave = respuesta.body()?.fotoReferencia?.trim()
                    if (!fotoReferenciaDelPedido(clave, pedidoId)) {
                        return ResultadoOperacion.Error(
                            TipoErrorPedido.SERVIDOR,
                            MensajesEntrega.CLAVE,
                            "FOTO_INVALIDA"
                        )
                    }
                    operaciones.guardarFoto(ambito, clave!!)
                    return ResultadoOperacion.Exito(clave)
                }
                val error = fallo<String>(respuesta)
                ultimo = error
                val reducido = if (paso == 0 && respuesta.code() == 413 && error is ResultadoOperacion.Error) {
                    (limiteBytesAnunciado(error.mensaje) ?: (limite / 2))
                        .takeIf { it in 32 * 1024 until limite }
                } else {
                    null
                }
                if (reducido != null) {
                    limite = reducido
                    preparado = compresor.jpeg(origen, limite) ?: return error
                } else {
                    return error
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                return falloRed(error)
            }
        }
        return ultimo
    }

    /** Separa uuid y clave de foto por usuario. El mismo teléfono no reutiliza la evidencia de otro repartidor. */
    private fun ambito(pedidoId: String): String? {
        val owner = almacen.usuarioActivoId()?.trim().orEmpty()
        val pedido = pedidoId.trim()
        if (owner.isEmpty() || pedido.isEmpty()) return null
        return "$owner|$pedido"
    }

    private fun uuidVigente(ambito: String): String {
        val actual = operaciones.uuidDe(ambito)?.trim()
        if (esUuidOperacion(actual)) return actual!!
        val nuevo = nuevoUuid().trim()
        val uuid = if (esUuidOperacion(nuevo)) nuevo else UUID.randomUUID().toString()
        operaciones.guardarUuid(ambito, uuid)
        return uuid
    }

    private fun tableroDesde(items: List<EntregaListaItemDto>): TableroReparto {
        val pedidos = items.mapNotNull { item -> pedidoDesde(item.pedido) }
        return TableroReparto(
            disponibles = pedidos.filter { it.estado.equals("LISTO", ignoreCase = true) }
                .sortedWith(compareBy(nullsLast()) { it.fecha }),
            enCamino = pedidos.filter { it.estado.equals("EN_CAMINO", ignoreCase = true) }
                .sortedWith(compareBy(nullsLast()) { it.fecha }),
            entregados = pedidos.filter { it.estado.equals("ENTREGADO", ignoreCase = true) }
                .sortedWith(compareByDescending(nullsLast()) { it.fecha })
        )
    }

    private fun detalleDesde(cuerpo: EntregaDetalleResponse?): DetalleReparto? {
        val pedido = pedidoDesde(cuerpo?.pedido) ?: return null
        val pago = pagoDesde(cuerpo?.pago)
        val entrega = evidenciaDesde(cuerpo?.entrega)
        return DetalleReparto(
            pedido = pedido,
            direccion = direccionDesde(cuerpo?.direccion),
            pago = pago,
            entrega = entrega,
            puedeFinalizar = puedeRegistrarEntrega(
                estadoPedido = pedido.estado,
                estadoPago = pago?.estado,
                yaEntregado = entrega != null || pedido.estado.equals("ENTREGADO", ignoreCase = true)
            )
        )
    }

    private fun pedidoDesde(dto: PedidoEntregaDto?): PedidoReparto? {
        val id = dto?.id?.trim().orEmpty()
        if (id.isEmpty()) return null
        return PedidoReparto(
            id = id,
            estado = dto?.estado?.trim().orEmpty(),
            totalCentimos = dto?.totalCentimos ?: 0L,
            fecha = dto?.fecha,
            repartidorId = dto?.repartidorId
        )
    }

    private fun direccionDesde(dto: DireccionEntregaDto?): DireccionReparto? {
        if (dto == null) return null
        val direccion = DireccionReparto(
            departamento = dto.departamento.orEmpty(),
            provincia = dto.provincia.orEmpty(),
            distrito = dto.distrito.orEmpty(),
            direccion = dto.direccion.orEmpty(),
            telefono = dto.telefono.orEmpty()
        )
        if (direccion.texto().isEmpty() && direccion.telefono.isBlank()) return null
        return direccion
    }

    private fun pagoDesde(dto: PagoDto?): PagoReparto? {
        val estado = dto?.estado?.trim().orEmpty()
        if (estado.isEmpty()) return null
        return PagoReparto(
            id = dto?.id?.trim()?.takeIf { it.isNotEmpty() },
            modalidad = dto?.modalidad?.trim().orEmpty().ifBlank { "ONLINE" },
            estado = estado,
            montoCentimos = dto?.montoCentimos
        )
    }

    private fun evidenciaDesde(dto: EntregaDto?): EvidenciaReparto? {
        val id = dto?.id?.trim().orEmpty()
        if (id.isEmpty()) return null
        return EvidenciaReparto(
            id = id,
            uuidOperacion = dto?.uuidOperacion?.trim().orEmpty(),
            fotoReferencia = dto?.fotoReferencia?.trim().orEmpty(),
            latitud = dto?.latitud ?: 0.0,
            longitud = dto?.longitud ?: 0.0,
            fechaEntrega = dto?.fechaEntrega
        )
    }

    private fun exigirRepartidor(): ResultadoOperacion<Nothing>? {
        val perfil = almacen.perfil() ?: return sesionVencida()
        if (perfil.rol.equals(RolOperativo.REPARTIDOR.name, ignoreCase = true)) return null
        return ResultadoOperacion.Error(
            TipoErrorPedido.PROHIBIDO,
            MensajesEntrega.SOLO_REPARTIDOR,
            "PERMISO_DENEGADO"
        )
    }

    private fun <T> sesionVencida(): ResultadoOperacion<T> =
        ResultadoOperacion.Error(TipoErrorPedido.SESION, MensajesSesion.EXPIRADA)

    private fun <T> falloRed(error: IOException): ResultadoOperacion<T> {
        if (error.message == ApiClient.MENSAJE_CUENTA_CAMBIADA) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.CUENTA,
                ApiClient.MENSAJE_CUENTA_CAMBIADA
            )
        }
        return ResultadoOperacion.Error(
            TipoErrorPedido.RED,
            error.message?.takeIf { it.isNotBlank() } ?: MensajesSesion.SIN_CONEXION
        )
    }

    private fun <T> fallo(respuesta: Response<*>): ResultadoOperacion<T> {
        val raw = respuesta.errorBody()?.string().orEmpty()
        val dto = runCatching { gson.fromJson(raw, ErrorApiDto::class.java) }.getOrNull()
        val mensaje = dto?.error?.takeIf { it.isNotBlank() }
            ?: raw.takeIf { it.isNotBlank() }
            ?: "Error HTTP ${respuesta.code()}"
        val tipo = when (respuesta.code()) {
            401 -> TipoErrorPedido.SESION
            403 -> TipoErrorPedido.PROHIBIDO
            404 -> TipoErrorPedido.NO_ENCONTRADO
            409 -> TipoErrorPedido.CONFLICTO
            else -> TipoErrorPedido.SERVIDOR
        }
        return ResultadoOperacion.Error(tipo, mensaje, dto?.codigo)
    }

    private fun <T> conFotoPublicada(resultado: ResultadoOperacion<T>): ResultadoOperacion<T> =
        if (resultado is ResultadoOperacion.Error) resultado.copy(fotoPublicada = true) else resultado
}

internal fun isoAhora(): String {
    val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formato.timeZone = TimeZone.getTimeZone("UTC")
    return formato.format(Date())
}
