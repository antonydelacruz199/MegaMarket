package com.megamarket.cliente.data.repository

import com.google.gson.Gson
import com.megamarket.cliente.data.local.OperacionPendiente
import com.megamarket.cliente.data.local.SyncMetadata
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.CrearPedidoRequest
import com.megamarket.cliente.data.remote.DireccionPedidoRequest
import com.megamarket.cliente.data.remote.ErrorApiDto
import com.megamarket.cliente.data.remote.ItemPedidoRequest
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoMovimientoInventario
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

/**
 * POST /api/pedidos ya descuenta stock y registra SALIDA_VENTA en Neon.
 * Por eso las operaciones locales CREAR_MOVIMIENTO_INVENTARIO (SALIDA_VENTA)
 * vinculadas a un pedido NO se reenvían a /api/inventario/movimientos.
 */
class SincronizacionRepository(
    private val api: ApiService,
    private val operacionDao: OperacionPendienteDao,
    private val pedidoDao: PedidoDao,
    private val productoDao: ProductoDao,
    private val metadatosSincronizacionDao: SyncMetadataDao,
    private val catalogoRemoto: CatalogoRemotoRepository,
    private val conectado: Flow<Boolean> = flowOf(false)
) {
    private val gson = Gson()

    data class ResumenSincronizacion(
        val pendientes: Int,
        val errores: Int,
        val ultimaExitosa: Long?,
        val ultimoError: String?,
        val conectado: Boolean = false,
        val enviando: Boolean = false
    )

    fun observarResumen(): Flow<ResumenSincronizacion> = combine(
        operacionDao.observarPendientesActivos(),
        operacionDao.observarErrores(),
        metadatosSincronizacionDao.observarMetadatos(),
        conectado
    ) { pendientes, errores, meta, hayInternet ->
        ResumenSincronizacion(
            pendientes = pendientes,
            errores = errores,
            ultimaExitosa = meta?.ultimaSincronizacionExitosa,
            ultimoError = meta?.ultimoErrorGeneral,
            conectado = hayInternet
        )
    }

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        operacionDao.recuperarEnviandoAtascadas(ahora)
        operacionDao.marcarLegacyStockComoError(
            "Operación legacy ACTUALIZAR_STOCK incompatible con movimientos de inventario.",
            ahora
        )
        registrarIntentoDeSincronizacion(ahora)

        val pendientes = operacionDao.obtenerOperacionesPorSincronizar()
        if (pendientes.isEmpty()) {
            catalogoRemoto.descargarYGuardarCatalogo()
            return@withContext ResultadoSincronizacion.SinTrabajo
        }

        var huboExito = false
        var reintentar: String? = null

        for (op in pendientes) {
            operacionDao.marcarOperacionEnviando(op.id, System.currentTimeMillis())
            when (val resultado = enviarOperacion(op)) {
                is ResultadoEnvio.Ok -> {
                    operacionDao.marcarOperacionSincronizada(op.id, System.currentTimeMillis())
                    huboExito = true
                }
                is ResultadoEnvio.ErrorDefinitivo -> {
                    operacionDao.marcarOperacionError(
                        op.id,
                        resultado.mensaje,
                        System.currentTimeMillis()
                    )
                    guardarErrorGeneral(resultado.mensaje)
                }
                is ResultadoEnvio.Reintentar -> {
                    operacionDao.actualizarOperacionPendiente(
                        op.copy(
                            estado = EstadoSincronizacion.PENDIENTE,
                            ultimoError = resultado.mensaje,
                            fechaActualizacion = System.currentTimeMillis()
                        )
                    )
                    reintentar = resultado.mensaje
                    break
                }
            }
        }

        catalogoRemoto.descargarYGuardarCatalogo()

        when {
            reintentar != null -> ResultadoSincronizacion.Reintentar(reintentar)
            huboExito -> {
                marcarExito(System.currentTimeMillis())
                ResultadoSincronizacion.Exito
            }
            else -> ResultadoSincronizacion.SinTrabajo
        }
    }

    private suspend fun enviarOperacion(op: OperacionPendiente): ResultadoEnvio {
        return try {
            when (op.operacion) {
                TipoOperacionPendiente.CREAR_PEDIDO -> enviarPedido(op)
                TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO -> {
                    // Stock de venta ya lo aplica POST /api/pedidos.
                    val tipo = try {
                        JSONObject(op.payload).optString("tipo")
                    } catch (_: Exception) {
                        ""
                    }
                    if (tipo == TipoMovimientoInventario.SALIDA_VENTA) {
                        ResultadoEnvio.Ok
                    } else {
                        ResultadoEnvio.ErrorDefinitivo(
                            "Movimiento no soportado en cliente: $tipo"
                        )
                    }
                }
                else -> ResultadoEnvio.ErrorDefinitivo("Operación no soportada: ${op.operacion}")
            }
        } catch (error: IOException) {
            ResultadoEnvio.Reintentar(error.message ?: "Error de red")
        } catch (error: Exception) {
            ResultadoEnvio.Reintentar(error.message ?: "Error inesperado")
        }
    }

    private suspend fun enviarPedido(op: OperacionPendiente): ResultadoEnvio {
        val pedido = pedidoDao.obtenerPedidoPorId(op.entidadIdLocal)
            ?: return ResultadoEnvio.ErrorDefinitivo("Pedido local no encontrado")
        val direccion = pedidoDao.obtenerDireccionPedido(pedido.id)
            ?: return ResultadoEnvio.ErrorDefinitivo("Dirección del pedido no encontrada")
        val detalles = pedidoDao.obtenerDetallesPedido(pedido.id)
        if (detalles.isEmpty()) {
            return ResultadoEnvio.ErrorDefinitivo("El pedido no tiene detalles")
        }

        val items = mutableListOf<ItemPedidoRequest>()
        for (detalle in detalles) {
            val producto = productoDao.obtenerPorId(detalle.productoId)
            val remoteId = producto?.remoteId
            if (remoteId.isNullOrBlank()) {
                return ResultadoEnvio.ErrorDefinitivo(
                    "Producto sin remoteId: ${detalle.nombreProducto}"
                )
            }
            items += ItemPedidoRequest(productoId = remoteId, cantidad = detalle.cantidad)
        }

        val respuesta = api.crearPedido(
            CrearPedidoRequest(
                clientUuid = pedido.clientUuid,
                direccion = DireccionPedidoRequest(
                    departamento = direccion.departamento,
                    provincia = direccion.provincia,
                    distrito = direccion.distrito,
                    direccion = direccion.direccion,
                    telefono = direccion.telefono
                ),
                items = items
            )
        )

        return clasificar(respuesta) {
            val cuerpo = respuesta.body()
            val remoto = cuerpo?.pedido
            pedidoDao.actualizarPedido(
                pedido.copy(
                    remoteId = remoto?.id ?: pedido.remoteId,
                    remoteVersion = remoto?.version ?: pedido.remoteVersion,
                    estadoSync = EstadoSincronizacion.SINCRONIZADO
                )
            )
            ResultadoEnvio.Ok
        }
    }

    private suspend fun <T> clasificar(
        respuesta: Response<T>,
        onOk: suspend () -> ResultadoEnvio
    ): ResultadoEnvio {
        val codigo = respuesta.code()
        if (respuesta.isSuccessful) return onOk()
        val mensaje = mensajeError(respuesta) ?: "Error HTTP $codigo"
        return when (codigo) {
            408, 429 -> ResultadoEnvio.Reintentar(mensaje)
            in 500..599 -> ResultadoEnvio.Reintentar(mensaje)
            409 -> ResultadoEnvio.ErrorDefinitivo("Conflicto: $mensaje")
            401 -> ResultadoEnvio.ErrorDefinitivo("Sesión expirada. Inicia sesión de nuevo.")
            403, 404, 400 -> ResultadoEnvio.ErrorDefinitivo(mensaje)
            else -> ResultadoEnvio.ErrorDefinitivo(mensaje)
        }
    }

    private fun <T> mensajeError(respuesta: Response<T>): String? {
        val raw = respuesta.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            gson.fromJson(raw, ErrorApiDto::class.java)?.error ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    private suspend fun registrarIntentoDeSincronizacion(ahora: Long) {
        val actual = metadatosSincronizacionDao.obtener()
        metadatosSincronizacionDao.guardar(
            SyncMetadata(
                id = SyncMetadata.FILA_UNICA,
                ultimaSincronizacionExitosa = actual?.ultimaSincronizacionExitosa,
                ultimoIntento = ahora,
                ultimoErrorGeneral = actual?.ultimoErrorGeneral
            )
        )
    }

    private suspend fun marcarExito(ahora: Long) {
        metadatosSincronizacionDao.guardar(
            SyncMetadata(
                id = SyncMetadata.FILA_UNICA,
                ultimaSincronizacionExitosa = ahora,
                ultimoIntento = ahora,
                ultimoErrorGeneral = null
            )
        )
    }

    private suspend fun guardarErrorGeneral(mensaje: String) {
        val actual = metadatosSincronizacionDao.obtener()
        metadatosSincronizacionDao.guardar(
            SyncMetadata(
                id = SyncMetadata.FILA_UNICA,
                ultimaSincronizacionExitosa = actual?.ultimaSincronizacionExitosa,
                ultimoIntento = System.currentTimeMillis(),
                ultimoErrorGeneral = mensaje
            )
        )
    }

    private sealed class ResultadoEnvio {
        data object Ok : ResultadoEnvio()
        data class ErrorDefinitivo(val mensaje: String) : ResultadoEnvio()
        data class Reintentar(val mensaje: String) : ResultadoEnvio()
    }
}

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val motivo: String?) : ResultadoSincronizacion()
}
