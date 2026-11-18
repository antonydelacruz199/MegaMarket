package com.megamarket.cliente.data.repository

import com.google.gson.Gson
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.ErrorApiDto
import com.megamarket.cliente.data.remote.SustitucionDecisionResponse
import com.megamarket.cliente.data.remote.SustitucionDto
import com.megamarket.modelo.SolicitudImagenProducto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class SustitucionesRepository(
    private val api: ApiService,
    private val productoDao: ProductoDao,
    private val pedidoDao: PedidoDao,
    private val medios: MediosImagenRepository,
    private val usuarioRemotoId: () -> String?,
    private val ownerLocalId: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun listar(estado: String? = null): ResultadoSustitucion<List<PropuestaSustitucionCliente>> =
        withContext(io) {
            val owner = usuarioRemotoId() ?: return@withContext sesionVencida()
            try {
                val respuesta = api.listarSustituciones(estado?.trim()?.takeIf { it.isNotEmpty() }, owner)
                if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
                val lista = respuesta.body()?.sustituciones.orEmpty().mapNotNull { enriquecer(it) }
                ResultadoSustitucion.Exito(lista)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                falloRed(error)
            }
        }

    suspend fun obtener(sustitucionId: String): ResultadoSustitucion<PropuestaSustitucionCliente> =
        withContext(io) {
            val owner = usuarioRemotoId() ?: return@withContext sesionVencida()
            consultarInterno(sustitucionId, owner)
        }

    suspend fun aceptar(sustitucionId: String): ResultadoSustitucion<PropuestaSustitucionCliente> =
        decidir(sustitucionId, aceptar = true)

    suspend fun rechazar(sustitucionId: String): ResultadoSustitucion<PropuestaSustitucionCliente> =
        decidir(sustitucionId, aceptar = false)

    suspend fun refrescarPedido(pedidoRemoteId: String?): ResultadoSustitucion<Long?> =
        withContext(io) {
            val owner = usuarioRemotoId() ?: return@withContext sesionVencida()
            val remoto = pedidoRemoteId?.trim().orEmpty()
            if (remoto.isEmpty()) {
                return@withContext ResultadoSustitucion.Exito(null)
            }
            try {
                val respuesta = api.obtenerPedido(remoto, owner)
                if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
                val pedidoApi = respuesta.body()?.pedido
                    ?: return@withContext ResultadoSustitucion.Error(
                        TipoErrorSustitucion.SERVIDOR,
                        MensajesSustitucionCliente.SERVIDOR
                    )
                val actual = pedidoDao.obtenerPorRemoteId(remoto)
                    ?.takeIf { it.clienteId == ownerLocalId() }
                if (actual != null) {
                    val versionRemota = pedidoApi.version
                    val versionLocal = actual.remoteVersion
                    // No sobrescribir Room con una respuesta más antigua.
                    if (versionRemota != null && versionLocal != null && versionRemota < versionLocal) {
                        return@withContext ResultadoSustitucion.Exito(actual.id)
                    }
                    val estado = pedidoApi.estado?.trim().orEmpty().ifBlank { actual.estado }
                    pedidoDao.actualizarPedido(
                        actual.copy(
                            estado = estado,
                            totalCentimos = pedidoApi.totalCentimos ?: actual.totalCentimos,
                            remoteVersion = versionRemota ?: actual.remoteVersion
                        )
                    )
                    return@withContext ResultadoSustitucion.Exito(actual.id)
                }
                ResultadoSustitucion.Exito(null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                falloRed(error)
            }
        }

    private suspend fun decidir(
        sustitucionId: String,
        aceptar: Boolean
    ): ResultadoSustitucion<PropuestaSustitucionCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencida()
        val id = sustitucionId.trim()
        if (id.isEmpty()) {
            return@withContext ResultadoSustitucion.Error(
                TipoErrorSustitucion.NO_ENCONTRADO,
                MensajesSustitucionCliente.NO_ENCONTRADA
            )
        }
        try {
            val respuesta = if (aceptar) {
                api.aceptarSustitucion(id, owner)
            } else {
                api.rechazarSustitucion(id, owner)
            }
            when {
                respuesta.isSuccessful -> {
                    val propuesta = respuesta.body()?.sustitucion?.let { enriquecer(it) }
                        ?: return@withContext ResultadoSustitucion.Error(
                            TipoErrorSustitucion.SERVIDOR,
                            MensajesSustitucionCliente.SERVIDOR
                        )
                    ResultadoSustitucion.Exito(propuesta)
                }
                respuesta.code() == 409 -> resolverConflictoDecision(respuesta, id, owner)
                else -> fallo(respuesta)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    private suspend fun resolverConflictoDecision(
        respuesta: Response<*>,
        sustitucionId: String,
        owner: String
    ): ResultadoSustitucion<PropuestaSustitucionCliente> {
        val crudo = runCatching { respuesta.errorBody()?.string() }.getOrNull().orEmpty()
        val dto = runCatching { gson.fromJson(crudo, SustitucionDecisionResponse::class.java) }.getOrNull()
            ?: runCatching { gson.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()?.let {
                SustitucionDecisionResponse(
                    sustitucion = it.sustitucion,
                    error = it.error,
                    codigo = it.codigo
                )
            }
        val desdeCuerpo = dto?.sustitucion?.let { enriquecer(it) }
        if (desdeCuerpo != null) {
            return ResultadoSustitucion.Exito(desdeCuerpo)
        }
        val consultada = consultarInterno(sustitucionId, owner)
        if (consultada is ResultadoSustitucion.Exito) return consultada
        val mensaje = dto?.error?.takeIf { it.isNotBlank() }
            ?: MensajesSustitucionCliente.CONFLICTO
        return ResultadoSustitucion.Error(
            TipoErrorSustitucion.CONFLICTO,
            mensaje,
            dto?.codigo,
            propuesta = (consultada as? ResultadoSustitucion.Error)?.propuesta
        )
    }

    private suspend fun consultarInterno(
        sustitucionId: String,
        owner: String
    ): ResultadoSustitucion<PropuestaSustitucionCliente> {
        val id = sustitucionId.trim()
        if (id.isEmpty()) {
            return ResultadoSustitucion.Error(
                TipoErrorSustitucion.NO_ENCONTRADO,
                MensajesSustitucionCliente.NO_ENCONTRADA
            )
        }
        return try {
            val respuesta = api.obtenerSustitucion(id, owner)
            if (!respuesta.isSuccessful) return fallo(respuesta)
            val propuesta = respuesta.body()?.sustitucion?.let { enriquecer(it) }
                ?: return ResultadoSustitucion.Error(
                    TipoErrorSustitucion.SERVIDOR,
                    MensajesSustitucionCliente.SERVIDOR
                )
            ResultadoSustitucion.Exito(propuesta)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRed(error)
        }
    }

    private suspend fun enriquecer(dto: SustitucionDto): PropuestaSustitucionCliente? {
        val id = dto.id?.trim().orEmpty()
        val detalle = dto.pedidoDetalleId?.trim().orEmpty()
        val estado = dto.estado?.trim().orEmpty()
        if (id.isEmpty() || detalle.isEmpty() || estado.isEmpty()) return null

        val originalId = dto.productoOriginalId?.trim()
        val sustitutoId = dto.productoSustitutoId?.trim()
        val original = originalId?.let { productoDao.obtenerPorRemoteId(it) }
        val sustituto = sustitutoId?.let { productoDao.obtenerPorRemoteId(it) }
        val precioOriginal = dto.precioOriginalCentimos ?: 0L
        val precioSustituto = dto.precioSustitutoCentimos ?: 0L
        val cantidad = dto.cantidad ?: 0
        val pedidoRemoto = dto.pedidoId?.trim()
        val pedidoLocal = pedidoRemoto
            ?.takeIf { it.isNotEmpty() }
            ?.let { pedidoDao.obtenerPorRemoteId(it) }
            ?.takeIf { it.clienteId == ownerLocalId() }

        return PropuestaSustitucionCliente(
            id = id,
            pedidoDetalleId = detalle,
            pedidoId = pedidoRemoto,
            pedidoLocalId = pedidoLocal?.id,
            productoOriginalId = originalId,
            productoSustitutoId = sustitutoId,
            nombreOriginal = original?.nombre?.trim().orEmpty().ifBlank { "Producto original" },
            nombreSustituto = sustituto?.nombre?.trim().orEmpty().ifBlank { "Producto sustituto" },
            marcaOriginal = original?.marca?.trim()?.takeIf { it.isNotEmpty() },
            marcaSustituto = sustituto?.marca?.trim()?.takeIf { it.isNotEmpty() },
            imagenOriginal = imagenDe(originalId, original?.imagenKey),
            imagenSustituto = imagenDe(sustitutoId, sustituto?.imagenKey),
            cantidad = cantidad,
            precioOriginalCentimos = precioOriginal,
            precioSustitutoCentimos = precioSustituto,
            importeClienteCentimos = importeClienteCentimos(precioOriginal, precioSustituto, cantidad),
            estado = estado.uppercase(),
            fechaPropuesta = dto.fechaPropuesta,
            fechaLimite = dto.fechaLimite,
            fechaRespuesta = dto.fechaRespuesta,
            respuestaAutomatica = dto.respuestaAutomatica == true
        )
    }

    private fun imagenDe(remoteId: String?, imagenKey: String?): SolicitudImagenProducto? =
        medios.solicitud(remoteId, imagenKey) ?: medios.solicitudPorId(remoteId)

    private fun <T> fallo(response: Response<*>): ResultadoSustitucion<T> {
        val (mensaje, codigo, _) = leerError(response)
        val tipo = when (response.code()) {
            401 -> TipoErrorSustitucion.SESION
            403 -> TipoErrorSustitucion.PROHIBIDO
            404 -> TipoErrorSustitucion.NO_ENCONTRADO
            409 -> TipoErrorSustitucion.CONFLICTO
            else -> TipoErrorSustitucion.SERVIDOR
        }
        val texto = when (tipo) {
            TipoErrorSustitucion.SESION -> mensaje.ifBlank { MensajesSustitucionCliente.SESION }
            TipoErrorSustitucion.PROHIBIDO -> mensaje.ifBlank { MensajesSustitucionCliente.PROHIBIDO }
            TipoErrorSustitucion.NO_ENCONTRADO ->
                mensaje.ifBlank { MensajesSustitucionCliente.NO_ENCONTRADA }
            TipoErrorSustitucion.CONFLICTO ->
                mensaje.ifBlank { MensajesSustitucionCliente.CONFLICTO }
            else -> mensaje.ifBlank { MensajesSustitucionCliente.SERVIDOR }
        }
        return ResultadoSustitucion.Error(tipo, texto, codigo, propuesta = null)
    }

    private fun leerError(response: Response<*>): Triple<String, String?, SustitucionDto?> {
        val crudo = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val dto = runCatching { gson.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()
        val mensaje = dto?.error?.takeIf { it.isNotBlank() } ?: mensajePorHttp(response.code())
        return Triple(mensaje, dto?.codigo, dto?.sustitucion)
    }

    private fun mensajePorHttp(codigo: Int): String = when (codigo) {
        401 -> MensajesSustitucionCliente.SESION
        403 -> MensajesSustitucionCliente.PROHIBIDO
        404 -> MensajesSustitucionCliente.NO_ENCONTRADA
        409 -> MensajesSustitucionCliente.CONFLICTO
        in 500..599 -> MensajesSustitucionCliente.SERVIDOR
        else -> "No se pudo completar la operación ($codigo)"
    }

    private fun <T> falloRed(error: IOException): ResultadoSustitucion<T> {
        if (error.message == "La cuenta activa cambió") {
            return ResultadoSustitucion.Error(
                TipoErrorSustitucion.CUENTA,
                MensajesSustitucionCliente.CUENTA
            )
        }
        return ResultadoSustitucion.Error(
            TipoErrorSustitucion.RED,
            MensajesSustitucionCliente.SIN_CONEXION
        )
    }

    private fun <T> sesionVencida(): ResultadoSustitucion<T> =
        ResultadoSustitucion.Error(TipoErrorSustitucion.SESION, MensajesSustitucionCliente.SESION)
}
