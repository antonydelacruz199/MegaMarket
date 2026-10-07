package com.megamarket.cliente.data.repository

import androidx.room.withTransaction
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.data.mapper.toEntity
import com.megamarket.cliente.data.mapper.toModel
import com.megamarket.cliente.model.CheckoutException
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.PedidoDetalle
import com.megamarket.cliente.model.ProductoSolicitado
import com.megamarket.cliente.model.ResultadoDescuentoStock
import com.megamarket.cliente.model.armarDetallesPedido
import com.megamarket.cliente.worker.StockSyncWorker

/**
 * Coordina pedido local (Room cliente) y descuento de stock (ContentProvider admin).
 * Son dos bases distintas: no hay una sola transacción Room; se usa compensación.
 */
class PedidoRepository(
    private val database: AppDatabase,
    private val pedidoDao: PedidoDao,
    private val carritoDao: CarritoDao,
    private val catalogo: CatalogoRepository,
    private val syncRepository: SyncRepository,
    private val programarSync: () -> Unit
) {
    /**
     * @throws CheckoutException con un mensaje listo para mostrar.
     */
    suspend fun confirmarPedido(clienteId: Long, direccion: Direccion): Long {
        direccion.error()?.let { throw CheckoutException(it) }

        val solicitados = carritoDao.obtenerTodos()
            .map { ProductoSolicitado(it.productoId, it.cantidad) }
        val catalogoActual = try {
            catalogo.leerActivos().associateBy { it.id }
        } catch (error: CatalogoNoDisponibleException) {
            throw CheckoutException(error.message ?: CatalogoRepository.MENSAJE_ERROR)
        }

        val detalles = try {
            armarDetallesPedido(solicitados, catalogoActual)
        } catch (error: CheckoutException) {
            if (error.productosNoDisponibles.isNotEmpty()) {
                carritoDao.eliminarVarios(error.productosNoDisponibles)
            }
            throw error
        }

        // Descuentos ya aplicados (para compensar si algo falla después).
        val descontados = mutableListOf<Pair<Long, Int>>()
        try {
            for (detalle in detalles) {
                when (
                    val resultado = catalogo.descontarStock(detalle.productoId, detalle.cantidad)
                ) {
                    is ResultadoDescuentoStock.Exito -> {
                        descontados += detalle.productoId to detalle.cantidad
                    }
                    is ResultadoDescuentoStock.Fallo -> {
                        restaurarDescuentos(descontados)
                        throw CheckoutException(resultado.mensaje)
                    }
                }
            }

            val pedidoId = try {
                database.withTransaction {
                    val id = pedidoDao.insertarPedido(
                        PedidoEntity(
                            clienteId = clienteId,
                            fecha = System.currentTimeMillis(),
                            totalCentimos = detalles.sumOf { it.subtotalCentimos },
                            estado = Pedido.ESTADO_CONFIRMADO
                        )
                    )
                    pedidoDao.insertarDireccion(direccion.toEntity(id))
                    pedidoDao.insertarDetalles(detalles.map { it.toEntity(id) })
                    carritoDao.vaciar()
                    id
                }
            } catch (error: Exception) {
                restaurarDescuentos(descontados)
                throw CheckoutException("No se pudo registrar el pedido")
            }

            try {
                registrarSincronizacion(detalles)
                programarSync()
            } catch (_: Exception) {
                // Pedido y stock local ya quedaron bien; la cola se reintentará al reabrir la app.
                try {
                    programarSync()
                } catch (_: Exception) {
                }
            }
            return pedidoId
        } catch (error: CheckoutException) {
            throw error
        } catch (error: CatalogoNoDisponibleException) {
            restaurarDescuentos(descontados)
            throw CheckoutException(error.message ?: CatalogoRepository.MENSAJE_ERROR)
        } catch (error: Exception) {
            restaurarDescuentos(descontados)
            throw CheckoutException("No se pudo actualizar el stock.")
        }
    }

    suspend fun obtenerPedido(pedidoId: Long): Pedido? = database.withTransaction {
        val pedido = pedidoDao.obtenerPedidoPorId(pedidoId) ?: return@withTransaction null
        val direccion = pedidoDao.obtenerDireccionPedido(pedidoId) ?: return@withTransaction null
        pedido.toModel(pedidoDao.obtenerDetallesPedido(pedidoId), direccion)
    }

    private suspend fun restaurarDescuentos(descontados: List<Pair<Long, Int>>) {
        for ((productoId, cantidad) in descontados.asReversed()) {
            try {
                catalogo.restaurarStock(productoId, cantidad)
            } catch (_: Exception) {
                // Mejor esfuerzo: el error original se propaga al llamador.
            }
        }
    }

    private suspend fun registrarSincronizacion(detalles: List<PedidoDetalle>) {
        for (detalle in detalles) {
            val stockFinal = catalogo.leerPorId(detalle.productoId)?.stock ?: continue
            syncRepository.registrarStockFinal(detalle.productoId, stockFinal)
        }
    }

    companion object {
        fun crearProgramador(contexto: android.content.Context): () -> Unit = {
            StockSyncWorker.programar(contexto)
        }
    }
}
