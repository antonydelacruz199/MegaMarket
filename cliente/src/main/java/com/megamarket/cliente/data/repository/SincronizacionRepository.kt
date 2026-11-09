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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
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
    private val sesion: com.megamarket.cliente.data.local.SesionSincronizacion,
    private val conectado: Flow<Boolean> = flowOf(false)
) {
    private val gson = Gson()
    private val mutex = Mutex()

    data class ResumenSincronizacion(
        val pendientes: Int,
        val errores: Int,
        val ultimaExitosa: Long?,
        val ultimoError: String?,
        val conectado: Boolean = false,
        val enviando: Boolean = false
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observarResumen(): Flow<ResumenSincronizacion> = sesion.owner.flatMapLatest { owner -> combine(
        operacionDao.observarPendientesActivos(owner),
        operacionDao.observarErrores(owner),
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

    }

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = mutex.withLock { withContext(Dispatchers.IO) {
        val owner=sesion.owner.value
        val remoteOwner=sesion.usuarioRemotoId ?: return@withContext ResultadoSincronizacion.SinTrabajo
        if(owner<=0 || sesion.accessToken.isNullOrBlank()) return@withContext ResultadoSincronizacion.SinTrabajo
        val ahora = System.currentTimeMillis()
        operacionDao.recuperarEnviandoAtascadas(ahora)
        operacionDao.marcarLegacyStockComoError(
            "Operación legacy ACTUALIZAR_STOCK incompatible con movimientos de inventario.",
            ahora
        )
        registrarIntentoDeSincronizacion(ahora)

        val pendientes = operacionDao.pendientesDelUsuario(owner)
        if (pendientes.isEmpty()) {
            val descarga=catalogoRemoto.descargarYGuardarCatalogo()
            return@withContext if(descarga is ResultadoDescargaCatalogo.Error) ResultadoSincronizacion.Reintentar(descarga.mensaje) else ResultadoSincronizacion.SinTrabajo
        }

        var huboExito = false
        var reintentar: String? = null

        for (op in pendientes) {
            if(sesion.owner.value!=owner || sesion.accessToken.isNullOrBlank()) break
            operacionDao.marcarOperacionEnviando(op.id, System.currentTimeMillis())
            when (val resultado = enviarOperacion(op,remoteOwner)) {
                is ResultadoEnvio.Ok -> {
                    operacionDao.marcarOperacionSincronizada(op.id, System.currentTimeMillis())
                    huboExito = true
                }
                is ResultadoEnvio.ErrorDefinitivo -> {
                    if(op.operacion==TipoOperacionPendiente.CREAR_PEDIDO) pedidoDao.obtenerPedidoPorId(op.entidadIdLocal)?.let { pedidoDao.actualizarPedido(it.copy(estado="REQUIERE_REVISION",estadoSync=EstadoSincronizacion.ERROR)) }
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

        val descarga=catalogoRemoto.descargarYGuardarCatalogo()
        if(descarga is ResultadoDescargaCatalogo.Error) return@withContext ResultadoSincronizacion.Reintentar(descarga.mensaje)

        when {
            reintentar != null -> ResultadoSincronizacion.Reintentar(reintentar)
            huboExito -> {
                Unit
                ResultadoSincronizacion.Exito
            }
            else -> ResultadoSincronizacion.SinTrabajo
        }
    }

    }

    private suspend fun enviarOperacion(op: OperacionPendiente, remoteOwner: String): ResultadoEnvio {
        return try {
            when (op.operacion) {
                "RESERVAR_CARRITO" -> {
                    val data=JSONObject(op.payload)
                    val array=data.getJSONArray("items")
                    val items=(0 until array.length()).map { val item=array.getJSONObject(it); ItemPedidoRequest(item.getString("productoId"),item.getInt("cantidad")) }
                    clasificar(api.reservarCarrito(data.getString("cartId"),com.megamarket.cliente.data.remote.ReservaRequest(data.getLong("revision"),items),remoteOwner)) {
                        operacionDao.cerrarReservasSuperadas(op.ownerId,op.id,"%"+data.getString("cartId")+"%")
                        ResultadoEnvio.Ok
                    }
                }
                TipoOperacionPendiente.CREAR_PEDIDO -> enviarPedido(op,remoteOwner)
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

    private suspend fun enviarPedido(op: OperacionPendiente, remoteOwner: String): ResultadoEnvio {
        val pedido = pedidoDao.obtenerPedidoPorId(op.entidadIdLocal)
            ?: return ResultadoEnvio.ErrorDefinitivo("Pedido local no encontrado")
        if(pedido.clienteId != sesion.owner.value || op.ownerId != pedido.clienteId) return ResultadoEnvio.ErrorDefinitivo("El pedido pertenece a otra cuenta")
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
            items += ItemPedidoRequest(productoId = remoteId, cantidad = detalle.cantidad, precioEsperadoCentimos = detalle.precioUnitarioCentimos)
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
            ), remoteOwner
        )

        return clasificar(respuesta) {
            val cuerpo = respuesta.body()
            val remoto = cuerpo?.pedido ?: return@clasificar ResultadoEnvio.Reintentar("Respuesta de pedido vacía")
            if(remoto.clienteId != remoteOwner || remoto.clientUuid != pedido.clientUuid) return@clasificar ResultadoEnvio.ErrorDefinitivo("Respuesta del servidor no corresponde al pedido")
            for(d in detalles) {
                val remoteId=productoDao.obtenerPorId(d.productoId)?.remoteId
                val final=cuerpo.detalles?.find { it.productoId==remoteId }
                if(final!=null) pedidoDao.actualizarDetalle(d.copy(precioUnitarioCentimos=final.precioUnitarioCentimos,subtotalCentimos=final.subtotalCentimos,cantidad=final.cantidad))
            }
            pedidoDao.actualizarPedido(
                pedido.copy(
                    remoteId = remoto.id ?: return@clasificar ResultadoEnvio.Reintentar("Pedido sin identificador"),
                    totalCentimos = remoto.totalCentimos ?: pedido.totalCentimos,
                    estado = remoto.estado ?: "CONFIRMADO",
                    remoteVersion = remoto.version ?: pedido.remoteVersion,
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
            401 -> ResultadoEnvio.Reintentar("Sesión expirada. Inicia sesión de nuevo.")
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
                sincronizadoElapsed=actual?.sincronizadoElapsed, bootCount=actual?.bootCount,
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
                sincronizadoElapsed=actual?.sincronizadoElapsed, bootCount=actual?.bootCount,
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
