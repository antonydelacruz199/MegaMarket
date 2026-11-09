package com.megamarket.cliente.data.repository

import androidx.room.withTransaction
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.dao.MovimientoInventarioDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.MovimientoInventario
import com.megamarket.cliente.data.local.OperacionPendiente
import com.megamarket.cliente.data.local.Pedido as PedidoRoom
import com.megamarket.cliente.data.mapper.toEntity
import com.megamarket.cliente.data.mapper.toModel
import com.megamarket.cliente.model.CheckoutException
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.ProductoSolicitado
import com.megamarket.cliente.model.armarDetallesPedido
import com.megamarket.cliente.worker.SincronizacionWorker
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoMovimientoInventario
import com.megamarket.modelo.TipoOperacionPendiente
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Checkout local-first atómico (Room cliente).
 * ContentProvider ya NO descuenta stock en compras.
 *
 * Remoto: POST /api/pedidos crea pedido, descuenta stock y registra SALIDA_VENTA.
 * Las ops locales CREAR_MOVIMIENTO_INVENTARIO (SALIDA_VENTA) no se reenvían.
 */
class PedidoRepository(
    private val database: AppDatabase,
    private val pedidoDao: PedidoDao,
    private val carritoDao: CarritoDao,
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoInventarioDao,
    private val operacionDao: OperacionPendienteDao,
    private val programarSincronizacion: () -> Unit,
    private val sesion: com.megamarket.cliente.data.local.SesionLocalStore
) {
    /**
     * @throws CheckoutException con un mensaje listo para mostrar.
     */
    suspend fun confirmarPedido(clienteId: Long, direccion: Direccion): Long {
        if(sesion.owner.value!=clienteId) throw CheckoutException("Tu sesión cambió. Vuelve a iniciar sesión.")
        val meta=database.metadatosSincronizacionDao().obtener()
        val ultima=meta?.ultimaSincronizacionExitosa
        if(!com.megamarket.cliente.model.FrescuraCatalogo.vigenteConReloj(ultima,System.currentTimeMillis(),meta?.sincronizadoElapsed,android.os.SystemClock.elapsedRealtime(),meta?.bootCount,sesion.bootCount()))
            throw CheckoutException("Tus datos llevan más de una hora sin actualizarse. Sincroniza para realizar el pedido.")
        direccion.error()?.let { throw CheckoutException(it) }

        val solicitados = carritoDao.obtenerTodos(clienteId)
            .map { ProductoSolicitado(it.productoId, it.cantidad) }
        if (solicitados.isEmpty()) {
            throw CheckoutException("El carrito está vacío")
        }

        val entidades = productoDao.obtenerActivos()
        if (entidades.isEmpty()) {
            throw CheckoutException(
                "Aún no hay datos descargados. Conéctate a Internet y sincroniza para cargar el catálogo."
            )
        }

        val catalogoModelo = entidades.associate { entidad ->
            entidad.id to entidad.toModel().copy(stock=entidad.stock+(solicitados.find { it.productoId==entidad.id }?.cantidad ?: 0))
        }

        val detalles = try {
            armarDetallesPedido(solicitados, catalogoModelo)
        } catch (error: CheckoutException) {
            if (error.productosNoDisponibles.isNotEmpty()) {
                Unit // Las reservas se retiran explícitamente desde el carrito.
            }
            throw error
        }

        val ahora = System.currentTimeMillis()
        val clientUuid = sesion.carritoId()

        return try {
            val pedidoId = database.withTransaction {
                if(sesion.owner.value!=clienteId) throw CheckoutException("Tu sesión cambió")
                if(!com.megamarket.cliente.model.FrescuraCatalogo.vigenteConReloj(ultima,System.currentTimeMillis(),meta?.sincronizadoElapsed,android.os.SystemClock.elapsedRealtime(),meta?.bootCount,sesion.bootCount())) throw CheckoutException("Sincroniza el catálogo antes de realizar el pedido")
                val actuales=carritoDao.obtenerTodos(clienteId).map { ProductoSolicitado(it.productoId,it.cantidad) }
                if(actuales!=solicitados) throw CheckoutException("El carrito cambió. Revisa tu compra.")
                val id = pedidoDao.insertarPedido(
                    PedidoRoom(
                        clienteId = clienteId,
                        clientUuid = clientUuid,
                        fecha = ahora,
                        totalCentimos = detalles.sumOf { it.subtotalCentimos },
                        estado = "PENDIENTE_CONFIRMACION",
                        estadoSync = EstadoSincronizacion.PENDIENTE
                    )
                )
                pedidoDao.insertarDireccion(direccion.toEntity(id))
                pedidoDao.insertarDetalles(detalles.map { it.toEntity(id) })

                operacionDao.insertar(
                    OperacionPendiente(
                        uuidOperacion = clientUuid,
                        tipoEntidad = TipoOperacionPendiente.PEDIDO,
                        ownerId = clienteId,
                        entidadIdLocal = id,
                        operacion = TipoOperacionPendiente.CREAR_PEDIDO,
                        payload = JSONObject()
                            .put("clientUuid", clientUuid)
                            .put("totalCentimos", detalles.sumOf { it.subtotalCentimos })
                            .toString(),
                        estado = EstadoSincronizacion.PENDIENTE,
                        fechaCreacion = ahora,
                        fechaActualizacion = ahora
                    )
                )

                carritoDao.vaciarCarrito(clienteId)
                sesion.rotarCarrito()
                id
            }
            try {
                programarSincronizacion()
            } catch (_: Exception) {
            }
            pedidoId
        } catch (error: CheckoutException) {
            throw error
        } catch (error: Exception) {
            throw CheckoutException(error.message ?: "No se pudo registrar el pedido")
        }
    }

    suspend fun obtenerPedido(pedidoId: Long): Pedido? = database.withTransaction {
        val pedido = pedidoDao.obtenerPedidoPorId(pedidoId)?.takeIf { it.clienteId==sesion.owner.value } ?: return@withTransaction null
        val direccion = pedidoDao.obtenerDireccionPedido(pedidoId) ?: return@withTransaction null
        pedido.toModel(pedidoDao.obtenerDetallesPedido(pedidoId), direccion)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observarPedidos(): kotlinx.coroutines.flow.Flow<List<PedidoRoom>> = sesion.owner.flatMapLatest { pedidoDao.observarDelUsuario(it) }

    suspend fun totalActual(pedidoId: Long): Long = database.withTransaction {
        val p=pedidoDao.obtenerPedidoPorId(pedidoId)?.takeIf { it.clienteId==sesion.owner.value } ?: throw CheckoutException("Pedido no disponible")
        pedidoDao.obtenerDetallesPedido(p.id).sumOf { d ->
            val producto=productoDao.obtenerPorId(d.productoId)?.takeIf { it.activo && it.remoteDeletedAt==null } ?: throw CheckoutException("Producto no disponible: "+d.nombreProducto)
            producto.toModel().precioVigenteCentimos * d.cantidad
        }
    }

    suspend fun aceptarPrecios(pedidoId: Long, totalAceptado: Long) = database.withTransaction {
        val p=pedidoDao.obtenerPedidoPorId(pedidoId)?.takeIf { it.clienteId==sesion.owner.value && it.estado=="REQUIERE_REVISION" } ?: throw CheckoutException("Pedido no disponible para revisión")
        val meta=database.metadatosSincronizacionDao().obtener()
        if(!com.megamarket.cliente.model.FrescuraCatalogo.vigenteConReloj(meta?.ultimaSincronizacionExitosa,System.currentTimeMillis(),meta?.sincronizadoElapsed,android.os.SystemClock.elapsedRealtime(),meta?.bootCount,sesion.bootCount())) throw CheckoutException("Sincroniza el catálogo antes de aceptar los precios")
        val total=totalActual(pedidoId)
        if(total!=totalAceptado) throw CheckoutException("El precio volvió a cambiar. Revisa el total")
        val op=operacionDao.obtenerPendienteDe(TipoOperacionPendiente.PEDIDO,p.id,TipoOperacionPendiente.CREAR_PEDIDO) ?: throw CheckoutException("No hay operación pendiente")
        for(d in pedidoDao.obtenerDetallesPedido(p.id)) {
            val precio=productoDao.obtenerPorId(d.productoId)!!.toModel().precioVigenteCentimos
            pedidoDao.actualizarDetalle(d.copy(precioUnitarioCentimos=precio,subtotalCentimos=precio*d.cantidad))
        }
        pedidoDao.actualizarPedido(p.copy(totalCentimos=total,estado="PENDIENTE_CONFIRMACION",estadoSync=EstadoSincronizacion.PENDIENTE))
        operacionDao.actualizarOperacionPendiente(op.copy(estado=EstadoSincronizacion.PENDIENTE,ultimoError=null))
        programarSincronizacion()
    }

    suspend fun cancelarPendiente(pedidoId: Long) = database.withTransaction {
        val p=pedidoDao.obtenerPedidoPorId(pedidoId)?.takeIf { it.clienteId==sesion.owner.value && it.estado=="REQUIERE_REVISION" } ?: throw CheckoutException("Solo puedes cancelar un pedido rechazado que requiere revisión")
        val op=operacionDao.obtenerPendienteDe(TipoOperacionPendiente.PEDIDO,p.id,TipoOperacionPendiente.CREAR_PEDIDO) ?: throw CheckoutException("No hay operación pendiente")
        operacionDao.marcarOperacionSincronizada(op.id,System.currentTimeMillis())
        pedidoDao.actualizarPedido(p.copy(estado="CANCELADO"))
        val now=System.currentTimeMillis()
        operacionDao.insertar(OperacionPendiente(uuidOperacion=UUID.randomUUID().toString(),tipoEntidad="CARRITO",entidadIdLocal=p.clienteId,ownerId=p.clienteId,operacion="RESERVAR_CARRITO",payload=JSONObject().put("cartId",p.clientUuid).put("revision",sesion.siguienteRevision(p.clientUuid)).put("items",org.json.JSONArray()).toString(),fechaCreacion=now))
        // Reconciliar liberación definitiva desde servidor evita incrementar dos veces el disponible.
        programarSincronizacion()
    }

    companion object {
        fun crearProgramador(contexto: android.content.Context): () -> Unit = {
            SincronizacionWorker.programar(contexto)
        }
    }
}
