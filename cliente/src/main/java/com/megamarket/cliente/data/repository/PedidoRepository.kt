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
import com.megamarket.cliente.model.ProductoSolicitado
import com.megamarket.cliente.model.armarDetallesPedido

class PedidoRepository(
    private val database: AppDatabase,
    private val pedidoDao: PedidoDao,
    private val carritoDao: CarritoDao,
    private val catalogo: CatalogoRepository
) {
    /**
     * Registra el pedido de forma atómica: si algo falla no queda pedido parcial
     * y el carrito se conserva. El stock del administrador no se modifica.
     *
     * @throws CheckoutException con un mensaje listo para mostrar.
     */
    suspend fun confirmarPedido(clienteId: Long, direccion: Direccion): Long {
        direccion.error()?.let { throw CheckoutException(it) }
        return try {
            database.withTransaction {
                val solicitados = carritoDao.obtenerTodos()
                    .map { ProductoSolicitado(it.productoId, it.cantidad) }
                val catalogoActual = catalogo.leerActivos().associateBy { it.id }
                val detalles = armarDetallesPedido(solicitados, catalogoActual)

                val pedidoId = pedidoDao.insertarPedido(
                    PedidoEntity(
                        clienteId = clienteId,
                        fecha = System.currentTimeMillis(),
                        totalCentimos = detalles.sumOf { it.subtotalCentimos },
                        estado = Pedido.ESTADO_CONFIRMADO
                    )
                )
                pedidoDao.insertarDireccion(direccion.toEntity(pedidoId))
                pedidoDao.insertarDetalles(detalles.map { it.toEntity(pedidoId) })
                carritoDao.vaciar()
                pedidoId
            }
        } catch (error: CheckoutException) {
            if (error.productosNoDisponibles.isNotEmpty()) {
                // Ya no se muestran en el carrito; si se quedan, el checkout nunca podría completarse.
                carritoDao.eliminarVarios(error.productosNoDisponibles)
            }
            throw error
        }
    }

    suspend fun obtenerPedido(pedidoId: Long): Pedido? = database.withTransaction {
        val pedido = pedidoDao.obtenerPedidoPorId(pedidoId) ?: return@withTransaction null
        val direccion = pedidoDao.obtenerDireccionPedido(pedidoId) ?: return@withTransaction null
        pedido.toModel(pedidoDao.obtenerDetallesPedido(pedidoId), direccion)
    }
}
