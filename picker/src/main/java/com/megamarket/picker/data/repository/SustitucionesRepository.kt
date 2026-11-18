package com.megamarket.picker.data.repository

import com.google.gson.Gson
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.dao.LineaPickingDao
import com.megamarket.picker.data.local.dao.OperacionPickingDao
import com.megamarket.picker.data.remote.ApiClient
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.CandidatoSustitutoDto
import com.megamarket.picker.data.remote.ErrorApiDto
import com.megamarket.picker.data.remote.PropuestaSustitucionRequest
import com.megamarket.picker.data.remote.SustitucionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class SustitucionesRepository(
    private val api: ApiService,
    private val almacen: AlmacenSesion,
    private val lineaDao: LineaPickingDao,
    private val operacionDao: OperacionPickingDao,
    private val pedidosRepository: PedidosPickerRepository,
    private val baseUrl: String,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun listarCandidatos(
        pedidoId: String,
        detalleId: String
    ): ResultadoOperacion<ListaCandidatosSustitucion> = withContext(io) {
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        val elegibilidad = validarLineaFaltanteConfirmada(pedidoId, detalleId, owner)
        if (elegibilidad != null) return@withContext elegibilidad

        try {
            val respuesta = api.listarSustitutos(pedidoId, detalleId, owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val body = respuesta.body()
                ?: return@withContext ResultadoOperacion.Error(
                    TipoErrorPedido.SERVIDOR,
                    "No se pudieron listar sustitutos"
                )
            val linea = lineaDao.obtener(detalleId, owner)
            val cantidad = linea?.cantidad ?: 1
            val precio = body.precioOriginalCentimos ?: 0L
            ResultadoOperacion.Exito(
                ListaCandidatosSustitucion(
                    detalleId = body.detalleId?.trim().orEmpty().ifBlank { detalleId },
                    precioOriginalCentimos = precio,
                    cantidadLinea = cantidad,
                    candidatos = body.candidatos.orEmpty().mapNotNull {
                        it.aDominio(precio, cantidad, baseUrl)
                    }
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    suspend fun proponer(
        pedidoId: String,
        detalleId: String,
        productoSustitutoId: String,
        cantidad: Int? = null
    ): ResultadoOperacion<SustitucionPropuesta> = withContext(io) {
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        val elegibilidad = validarLineaFaltanteConfirmada(pedidoId, detalleId, owner)
        if (elegibilidad != null) return@withContext elegibilidad

        val producto = productoSustitutoId.trim()
        if (producto.isEmpty()) {
            return@withContext ResultadoOperacion.Error(
                TipoErrorPedido.SERVIDOR,
                MensajesSustitucion.SELECCIONA
            )
        }

        try {
            val respuesta = api.proponerSustitucion(
                id = pedidoId,
                detalleId = detalleId,
                body = PropuestaSustitucionRequest(
                    productoSustitutoId = producto,
                    cantidad = cantidad
                ),
                owner = owner
            )
            if (respuesta.isSuccessful) {
                val propuesta = respuesta.body()?.sustitucion?.aDominio()
                    ?: return@withContext ResultadoOperacion.Error(
                        TipoErrorPedido.SERVIDOR,
                        "No se pudo leer la propuesta registrada"
                    )
                return@withContext ResultadoOperacion.Exito(propuesta)
            }

            if (respuesta.code() == 409) {
                val (mensaje, codigo, sustitucionId) = leerErrorExtendido(respuesta)
                if (codigo == "PROPUESTA_PENDIENTE" && !sustitucionId.isNullOrBlank()) {
                    val existente = consultarSustitucionInterno(sustitucionId, owner)
                    if (existente is ResultadoOperacion.Exito) return@withContext existente
                }
                return@withContext ResultadoOperacion.Error(
                    TipoErrorPedido.CONFLICTO,
                    mensaje.ifBlank { MensajesSustitucion.CONFLICTO },
                    codigo
                )
            }
            fallo(respuesta)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    suspend fun consultarSustitucion(
        sustitucionId: String
    ): ResultadoOperacion<SustitucionPropuesta> = withContext(io) {
        val owner = almacen.usuarioActivoId() ?: return@withContext sesionVencida()
        consultarSustitucionInterno(sustitucionId, owner)
    }

    suspend fun refrescarPreparacion(pedidoId: String): ResultadoOperacion<PreparacionPedido> =
        pedidosRepository.obtenerPreparacion(pedidoId)

    private suspend fun consultarSustitucionInterno(
        sustitucionId: String,
        owner: String
    ): ResultadoOperacion<SustitucionPropuesta> {
        val id = sustitucionId.trim()
        if (id.isEmpty()) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.NO_ENCONTRADO,
                MensajesSustitucion.NO_ENCONTRADA
            )
        }
        return try {
            val respuesta = api.obtenerSustitucion(id, owner)
            if (!respuesta.isSuccessful) return fallo(respuesta)
            val propuesta = respuesta.body()?.sustitucion?.aDominio()
                ?: return ResultadoOperacion.Error(
                    TipoErrorPedido.SERVIDOR,
                    "No se pudo leer el estado de la sustitución"
                )
            ResultadoOperacion.Exito(propuesta)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    private suspend fun validarLineaFaltanteConfirmada(
        pedidoId: String,
        detalleId: String,
        owner: String
    ): ResultadoOperacion.Error? {
        val linea = lineaDao.obtener(detalleId, owner)
            ?: return ResultadoOperacion.Error(
                TipoErrorPedido.NO_ENCONTRADO,
                MensajesSustitucion.NO_ENCONTRADA
            )
        if (linea.pedidoId != pedidoId) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.NO_ENCONTRADO,
                MensajesSustitucion.NO_ENCONTRADA
            )
        }
        if (operacionDao.faltantesActivos(detalleId, owner) > 0) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.CONFLICTO,
                MensajesSustitucion.FALTANTE_PENDIENTE,
                "FALTANTE_PENDIENTE_LOCAL"
            )
        }
        if (!linea.estadoPicking.equals("FALTANTE", ignoreCase = true)) {
            return ResultadoOperacion.Error(
                TipoErrorPedido.CONFLICTO,
                MensajesSustitucion.SOLO_FALTANTE,
                "ESTADO_PICKING_INVALIDO"
            )
        }
        return null
    }

    private fun <T> fallo(response: Response<*>): ResultadoOperacion<T> {
        val (mensaje, codigo, _) = leerErrorExtendido(response)
        val tipo = when (response.code()) {
            401 -> TipoErrorPedido.SESION
            403 -> TipoErrorPedido.PROHIBIDO
            404 -> TipoErrorPedido.NO_ENCONTRADO
            409 -> TipoErrorPedido.CONFLICTO
            else -> TipoErrorPedido.SERVIDOR
        }
        val texto = when (tipo) {
            TipoErrorPedido.SESION ->
                mensaje.ifBlank { MensajesSustitucion.SESION }
            TipoErrorPedido.PROHIBIDO ->
                mensaje.ifBlank { MensajesSustitucion.PROHIBIDO }
            TipoErrorPedido.NO_ENCONTRADO ->
                mensaje.ifBlank { MensajesSustitucion.NO_ENCONTRADA }
            TipoErrorPedido.CONFLICTO ->
                mensaje.ifBlank { MensajesSustitucion.CONFLICTO }
            else -> mensaje.ifBlank { MensajesSesion.SERVIDOR }
        }
        return ResultadoOperacion.Error(tipo, texto, codigo)
    }

    private fun leerErrorExtendido(response: Response<*>): Triple<String, String?, String?> {
        val crudo = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val dto = runCatching { gson.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()
        val mensaje = dto?.error?.takeIf { it.isNotBlank() }
            ?: mensajePorHttp(response.code())
        return Triple(mensaje, dto?.codigo, dto?.sustitucionId)
    }

    private fun mensajePorHttp(codigo: Int): String = when (codigo) {
        401 -> MensajesSustitucion.SESION
        403 -> MensajesSustitucion.PROHIBIDO
        404 -> MensajesSustitucion.NO_ENCONTRADA
        409 -> MensajesSustitucion.CONFLICTO
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
        return ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
    }

    private fun <T> sesionVencida(): ResultadoOperacion<T> =
        ResultadoOperacion.Error(TipoErrorPedido.SESION, MensajesSustitucion.SESION)
}

private fun CandidatoSustitutoDto.aDominio(
    precioOriginal: Long,
    cantidad: Int,
    baseUrl: String
): CandidatoSustituto? {
    val id = id?.trim().orEmpty()
    if (id.isEmpty()) return null
    return CandidatoSustituto(
        id = id,
        nombre = nombre?.trim().orEmpty().ifBlank { "Producto" },
        marca = marca?.trim()?.takeIf { it.isNotEmpty() },
        cantidad = cantidad,
        precioOriginalCentimos = precioOriginal,
        precioSustitutoCentimos = precioCentimos ?: 0L,
        pasillo = pasillo,
        codigoBarras = codigoBarras,
        stockDisponible = stockDisponible ?: 0,
        imagenUrl = urlImagenProducto(baseUrl, id)
    )
}

private fun SustitucionDto.aDominio(): SustitucionPropuesta? {
    val id = id?.trim().orEmpty()
    val detalle = pedidoDetalleId?.trim().orEmpty()
    val estado = estado?.trim().orEmpty()
    if (id.isEmpty() || detalle.isEmpty() || estado.isEmpty()) return null
    return SustitucionPropuesta(
        id = id,
        pedidoDetalleId = detalle,
        pedidoId = pedidoId,
        productoOriginalId = productoOriginalId,
        productoSustitutoId = productoSustitutoId,
        cantidad = cantidad ?: 0,
        precioOriginalCentimos = precioOriginalCentimos ?: 0L,
        precioSustitutoCentimos = precioSustitutoCentimos ?: 0L,
        estado = estado.uppercase(),
        fechaPropuesta = fechaPropuesta,
        fechaLimite = fechaLimite,
        fechaRespuesta = fechaRespuesta,
        respuestaAutomatica = respuestaAutomatica == true
    )
}
