package com.megamarket.picker.data.repository

import com.google.gson.Gson
import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity
import com.megamarket.picker.data.local.dao.LineaPickingDao
import com.megamarket.picker.data.local.dao.OperacionPickingDao
import com.megamarket.picker.data.remote.ApiClient
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.EscaneoRequest
import com.megamarket.picker.data.remote.EscaneoResponse
import com.megamarket.picker.data.remote.FaltanteResponse
import com.megamarket.picker.escaneo.CANTIDAD_ESCANEO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class SincronizacionPickingRepository(
    private val api: ApiService,
    private val operacionDao: OperacionPickingDao,
    private val lineaDao: LineaPickingDao,
    private val almacen: AlmacenSesion,
    private val baseUrl: String,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()
    private val mutex = Mutex()

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = mutex.withLock {
        withContext(io) {
            val owner = almacen.usuarioActivoId()?.trim().orEmpty()
            if (owner.isEmpty() || almacen.tokenActual().isNullOrBlank()) {
                return@withContext ResultadoSincronizacion.SinTrabajo
            }
            val ahora = System.currentTimeMillis()
            operacionDao.recuperarEnviandoAtascadas(ahora)
            val pendientes = operacionDao.pendientesDelUsuario(owner)
            if (pendientes.isEmpty()) return@withContext ResultadoSincronizacion.SinTrabajo

            var huboExito = false
            var reintentar: String? = null

            for (op in pendientes) {
                if (almacen.usuarioActivoId() != owner || almacen.tokenActual().isNullOrBlank()) {
                    reintentar = MensajesSesion.EXPIRADA
                    break
                }
                if (op.ownerId != owner) continue

                val marcado = operacionDao.marcarEnviando(op.id, owner, System.currentTimeMillis())
                if (marcado == 0) continue

                when (val resultado = enviar(op, owner)) {
                    is ResultadoEnvio.ConfirmadoCorrecto -> {
                        resultado.armada?.let { armada ->
                            lineaDao.actualizarConfirmada(
                                id = op.detalleId,
                                ownerId = owner,
                                armada = armada,
                                estado = resultado.estadoPicking ?: "PENDIENTE",
                                ahora = System.currentTimeMillis()
                            )
                        }
                        operacionDao.marcarSincronizada(
                            op.id,
                            owner,
                            OperacionPicking.RESULTADO_CORRECTO,
                            System.currentTimeMillis()
                        )
                        huboExito = true
                    }
                    is ResultadoEnvio.ConfirmadoIncorrecto -> {
                        operacionDao.marcarSincronizada(
                            op.id,
                            owner,
                            OperacionPicking.RESULTADO_INCORRECTO,
                            System.currentTimeMillis()
                        )
                        huboExito = true
                    }
                    is ResultadoEnvio.Ok -> {
                        operacionDao.marcarSincronizada(
                            op.id,
                            owner,
                            null,
                            System.currentTimeMillis()
                        )
                        huboExito = true
                    }
                    is ResultadoEnvio.ErrorDefinitivo -> {
                        operacionDao.marcarError(
                            id = op.id,
                            ownerId = owner,
                            error = resultado.mensaje,
                            ahora = System.currentTimeMillis(),
                            codigo = resultado.codigo
                        )
                    }
                    is ResultadoEnvio.Reintentar -> {
                        operacionDao.devolverAPendiente(
                            op.id,
                            owner,
                            resultado.mensaje,
                            System.currentTimeMillis()
                        )
                        reintentar = resultado.mensaje
                        break
                    }
                }
            }

            when {
                reintentar != null -> ResultadoSincronizacion.Reintentar(reintentar)
                huboExito -> ResultadoSincronizacion.Exito
                else -> ResultadoSincronizacion.SinTrabajo
            }
        }
    }

    private suspend fun enviar(op: OperacionPickingEntity, owner: String): ResultadoEnvio {
        return try {
            when (op.operacion) {
                OperacionPicking.ESCANEAR -> enviarEscaneo(op, owner)
                OperacionPicking.MARCAR_FALTANTE -> enviarFaltante(op, owner)
                else -> ResultadoEnvio.ErrorDefinitivo("Operación no soportada: ${op.operacion}")
            }
        } catch (error: IOException) {
            if (error.message == ApiClient.MENSAJE_CUENTA_CAMBIADA) {
                ResultadoEnvio.Reintentar(ApiClient.MENSAJE_CUENTA_CAMBIADA)
            } else {
                ResultadoEnvio.Reintentar(MensajesSesion.SIN_CONEXION)
            }
        } catch (error: Exception) {
            ResultadoEnvio.Reintentar(error.message ?: "Error inesperado")
        }
    }

    private suspend fun enviarEscaneo(op: OperacionPickingEntity, owner: String): ResultadoEnvio {
        val codigo = op.codigoBarras.orEmpty()
        if (codigo.isBlank()) {
            return ResultadoEnvio.ErrorDefinitivo("codigoBarras es obligatorio", "CODIGO_REQUERIDO")
        }
        val respuesta = api.registrarEscaneo(
            id = op.pedidoId,
            detalleId = op.detalleId,
            body = EscaneoRequest(
                uuidOperacion = op.uuidOperacion,
                codigoBarras = codigo,
                cantidad = op.cantidad.takeIf { it > 0 } ?: CANTIDAD_ESCANEO
            ),
            owner = owner
        )
        val cuerpo = cuerpoEscaneo(respuesta)
        return clasificarEscaneo(respuesta.code(), cuerpo)
    }

    private suspend fun enviarFaltante(op: OperacionPickingEntity, owner: String): ResultadoEnvio {
        val respuesta = api.marcarFaltante(op.pedidoId, op.detalleId, owner)
        val cuerpo = if (respuesta.isSuccessful) {
            respuesta.body()
        } else {
            val crudo = runCatching { respuesta.errorBody()?.string() }.getOrNull()
            runCatching { gson.fromJson(crudo, FaltanteResponse::class.java) }.getOrNull()
        }
        val codigo = cuerpo?.codigo
        val mensaje = cuerpo?.error
        return when {
            respuesta.isSuccessful -> {
                cuerpo?.detalle?.let { detalle ->
                    lineaDao.actualizarConfirmada(
                        id = op.detalleId,
                        ownerId = owner,
                        armada = detalle.cantidadArmada ?: 0,
                        estado = detalle.estadoPicking?.trim().orEmpty().ifBlank { "FALTANTE" },
                        ahora = System.currentTimeMillis()
                    )
                }
                ResultadoEnvio.Ok
            }
            respuesta.code() == 401 -> ResultadoEnvio.Reintentar(mensaje ?: MensajesSesion.EXPIRADA)
            respuesta.code() in listOf(408, 429) || respuesta.code() in 500..599 ->
                ResultadoEnvio.Reintentar(mensaje ?: MensajesSesion.SERVIDOR)
            else -> ResultadoEnvio.ErrorDefinitivo(
                mensaje ?: "No se pudo marcar el faltante",
                codigo
            )
        }
    }

    private fun clasificarEscaneo(http: Int, cuerpo: EscaneoResponse?): ResultadoEnvio {
        val codigo = cuerpo?.codigo
        val resultado = cuerpo?.escaneo?.resultado
        val mensaje = cuerpo?.error
        val detalle = cuerpo?.detalle
        if (http == 401) {
            return ResultadoEnvio.Reintentar(mensaje ?: MensajesSesion.EXPIRADA)
        }
        if (codigo == "CODIGO_INCORRECTO" || resultado == "INCORRECTO") {
            return ResultadoEnvio.ConfirmadoIncorrecto(mensaje)
        }
        if (codigo == "CANTIDAD_EXCEDIDA" || codigo == "PEDIDO_NO_ASIGNADO" ||
            codigo == "ESTADO_PICKING_INVALIDO" || codigo == "DETALLE_NO_ENCONTRADO" ||
            codigo == "UUID_EN_USO"
        ) {
            return ResultadoEnvio.ErrorDefinitivo(mensaje ?: "Conflicto de picking", codigo)
        }
        if ((http == 200 || http == 201) && (resultado == "CORRECTO" || resultado == null)) {
            return ResultadoEnvio.ConfirmadoCorrecto(
                armada = detalle?.cantidadArmada,
                estadoPicking = detalle?.estadoPicking,
                mensaje = mensaje
            )
        }
        return when (http) {
            408, 429 -> ResultadoEnvio.Reintentar(mensaje ?: "Reintentar")
            in 500..599 -> ResultadoEnvio.Reintentar(mensaje ?: MensajesSesion.SERVIDOR)
            403, 404, 400, 409 -> ResultadoEnvio.ErrorDefinitivo(
                mensaje ?: "No se pudo registrar el escaneo",
                codigo
            )
            else -> ResultadoEnvio.ErrorDefinitivo(mensaje ?: "Error HTTP $http", codigo)
        }
    }

    private fun cuerpoEscaneo(respuesta: Response<EscaneoResponse>): EscaneoResponse? {
        return if (respuesta.isSuccessful) {
            respuesta.body()
        } else {
            val crudo = runCatching { respuesta.errorBody()?.string() }.getOrNull()
            runCatching { gson.fromJson(crudo, EscaneoResponse::class.java) }.getOrNull()
        }
    }
}
