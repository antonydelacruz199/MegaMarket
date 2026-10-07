package com.megamarket.cliente.data.repository

import androidx.room.withTransaction
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.dao.MovimientoInventarioDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.entities.MovimientoInventarioEntity
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.data.mapper.toEntity
import com.megamarket.cliente.data.mapper.toModel
import com.megamarket.cliente.model.CheckoutException
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.ProductoSolicitado
import com.megamarket.cliente.model.armarDetallesPedido
import com.megamarket.cliente.worker.SyncWorker
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoMovimientoInventario
import com.megamarket.modelo.TipoOperacionPendiente
import org.json.JSONObject
import java.util.UUID

/**
 * Checkout local-first atómico (Room cliente).
 * ContentProvider ya NO descuenta stock en compras.
 *
 * Contrato futuro:
 * - POST /api/pedidos → pedido/detalles/dirección (sin descontar stock)
 * - POST /api/inventario/movimientos → único responsable del stock remoto
 */
class PedidoRepository(
    private val database: AppDatabase,
    private val pedidoDao: PedidoDao,
    private val carritoDao: CarritoDao,
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoInventarioDao,
    private val operacionDao: OperacionPendienteDao,
    private val programarSync: () -> Unit
) {
    /**
     * @throws CheckoutException con un mensaje listo para mostrar.
     */
    suspend fun confirmarPedido(clienteId: Long, direccion: Direccion): Long {
        direccion.error()?.let { throw CheckoutException(it) }

        val solicitados = carritoDao.obtenerTodos()
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
            entidad.id to entidad.toModel()
        }

        val detalles = try {
            armarDetallesPedido(solicitados, catalogoModelo)
        } catch (error: CheckoutException) {
            if (error.productosNoDisponibles.isNotEmpty()) {
                carritoDao.eliminarVarios(error.productosNoDisponibles)
            }
            throw error
        }

        val ahora = System.currentTimeMillis()
        val clientUuid = UUID.randomUUID().toString()

        return try {
            val pedidoId = database.withTransaction {
                for (detalle in detalles) {
                    val filas = productoDao.descontarStockLocal(detalle.productoId, detalle.cantidad)
                    if (filas == 0) {
                        throw CheckoutException(
                            "Stock insuficiente para ${detalle.nombreProducto}."
                        )
                    }
                }

                val id = pedidoDao.insertarPedido(
                    PedidoEntity(
                        clienteId = clienteId,
                        clientUuid = clientUuid,
                        fecha = ahora,
                        totalCentimos = detalles.sumOf { it.subtotalCentimos },
                        estado = Pedido.ESTADO_CONFIRMADO,
                        estadoSync = EstadoSincronizacion.PENDIENTE
                    )
                )
                pedidoDao.insertarDireccion(direccion.toEntity(id))
                pedidoDao.insertarDetalles(detalles.map { it.toEntity(id) })

                operacionDao.insertar(
                    OperacionPendienteEntity(
                        uuidOperacion = clientUuid,
                        tipoEntidad = TipoOperacionPendiente.PEDIDO,
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

                for (detalle in detalles) {
                    val uuidMov = UUID.randomUUID().toString()
                    movimientoDao.insertar(
                        MovimientoInventarioEntity(
                            uuidOperacion = uuidMov,
                            productoId = detalle.productoId,
                            pedidoId = id,
                            tipo = TipoMovimientoInventario.SALIDA_VENTA,
                            cantidad = detalle.cantidad,
                            fechaCreacion = ahora
                        )
                    )
                    operacionDao.insertar(
                        OperacionPendienteEntity(
                            uuidOperacion = uuidMov,
                            tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                            entidadIdLocal = detalle.productoId,
                            operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
                            payload = JSONObject()
                                .put("productoIdLocal", detalle.productoId)
                                .put("tipo", TipoMovimientoInventario.SALIDA_VENTA)
                                .put("cantidad", detalle.cantidad)
                                .put("pedidoUuid", clientUuid)
                                .toString(),
                            estado = EstadoSincronizacion.PENDIENTE,
                            fechaCreacion = ahora,
                            fechaActualizacion = ahora
                        )
                    )
                }

                carritoDao.vaciar()
                id
            }
            try {
                programarSync()
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
        val pedido = pedidoDao.obtenerPedidoPorId(pedidoId) ?: return@withTransaction null
        val direccion = pedidoDao.obtenerDireccionPedido(pedidoId) ?: return@withTransaction null
        pedido.toModel(pedidoDao.obtenerDetallesPedido(pedidoId), direccion)
    }

    companion object {
        fun crearProgramador(contexto: android.content.Context): () -> Unit = {
            SyncWorker.programar(contexto)
        }
    }
}
