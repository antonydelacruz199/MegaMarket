package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.remote.RetrofitProvider
import com.megamarket.cliente.data.remote.StockSyncPayload
import com.megamarket.cliente.data.remote.api.StockApi
import com.megamarket.cliente.data.remote.dto.ActualizarStockRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cola local + envío a API REST.
 *
 * Falta para Neon: API desplegada + BASE_URL + mapeo entidadIdLocal (Long) → UUID remoto.
 * Sin eso, [sincronizarPendientes] pide reintento y no borra operaciones.
 */
class SyncRepository(
    private val dao: OperacionPendienteDao,
    private val baseUrlApi: String,
    private val resolverUuidRemoto: (productoIdLocal: Long) -> String? = { null }
) {
    private val api: StockApi? by lazy { RetrofitProvider.crearStockApi(baseUrlApi) }

    /**
     * Una sola operación PENDIENTE de stock por producto; el payload siempre es el stock final.
     */
    suspend fun registrarStockFinal(productoIdLocal: Long, stockFinal: Int) = withContext(Dispatchers.IO) {
        val payload = StockSyncPayload(productoIdLocal, stockFinal).aJson()
        val existente = dao.obtenerPendienteDe(
            tipoEntidad = OperacionPendienteEntity.TIPO_PRODUCTO,
            entidadIdLocal = productoIdLocal,
            operacion = OperacionPendienteEntity.OPERACION_ACTUALIZAR_STOCK
        )
        if (existente != null) {
            dao.actualizar(
                existente.copy(
                    payload = payload,
                    fechaCreacion = System.currentTimeMillis(),
                    intentos = 0,
                    ultimoError = null,
                    estado = OperacionPendienteEntity.ESTADO_PENDIENTE
                )
            )
        } else {
            dao.insertar(
                OperacionPendienteEntity(
                    tipoEntidad = OperacionPendienteEntity.TIPO_PRODUCTO,
                    entidadIdLocal = productoIdLocal,
                    operacion = OperacionPendienteEntity.OPERACION_ACTUALIZAR_STOCK,
                    payload = payload,
                    fechaCreacion = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = withContext(Dispatchers.IO) {
        val pendientes = dao.obtenerPendientes()
        if (pendientes.isEmpty()) return@withContext ResultadoSincronizacion.SinTrabajo

        val servicio = api
        if (servicio == null) {
            return@withContext ResultadoSincronizacion.Reintentar(
                "API REST no configurada. Defina MEGAMARKET_API_BASE_URL cuando exista el backend."
            )
        }

        var huboExito = false
        var debeReintentar = false
        var ultimoMensaje: String? = null

        for (operacion in pendientes) {
            if (operacion.operacion != OperacionPendienteEntity.OPERACION_ACTUALIZAR_STOCK) {
                dao.marcarError(operacion.id, "Operación no soportada: ${operacion.operacion}")
                debeReintentar = true
                continue
            }
            val uuid = resolverUuidRemoto(operacion.entidadIdLocal)
            if (uuid.isNullOrBlank()) {
                dao.marcarError(
                    operacion.id,
                    "Falta UUID remoto del producto ${operacion.entidadIdLocal}. " +
                        "No se marca como sincronizado."
                )
                debeReintentar = true
                ultimoMensaje = "No se pudo sincronizar todavía. Se reintentará cuando haya conexión."
                continue
            }

            dao.marcarSincronizando(operacion.id)
            try {
                val payload = StockSyncPayload.desdeJson(operacion.payload)
                val respuesta = servicio.actualizarStock(
                    uuidProducto = uuid,
                    body = ActualizarStockRequest(stock = payload.stock)
                )
                if (respuesta.isSuccessful) {
                    dao.eliminar(operacion.id)
                    huboExito = true
                } else {
                    dao.marcarError(operacion.id, "HTTP ${respuesta.code()}")
                    debeReintentar = true
                    ultimoMensaje = "No se pudo sincronizar todavía. Se reintentará cuando haya conexión."
                }
            } catch (error: Exception) {
                dao.marcarError(operacion.id, error.message ?: "Error de red")
                debeReintentar = true
                ultimoMensaje = "No se pudo sincronizar todavía. Se reintentará cuando haya conexión."
            }
        }

        when {
            debeReintentar -> ResultadoSincronizacion.Reintentar(ultimoMensaje)
            huboExito -> ResultadoSincronizacion.Exito
            else -> ResultadoSincronizacion.SinTrabajo
        }
    }
}

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val motivo: String?) : ResultadoSincronizacion()
}
