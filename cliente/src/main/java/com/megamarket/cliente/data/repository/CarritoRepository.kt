package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.CarritoItem
import com.megamarket.cliente.model.LineaCarrito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class CarritoRepository(
    private val carritoDao: CarritoDao,
    private val catalogo: CatalogoRepository
) {
    /**
     * Combina carrito Room + productos Room. Se actualiza cuando cambia el stock cacheado.
     */
    fun observarLineasCarrito(): Flow<List<LineaCarrito>> = combine(
        carritoDao.observarItems(),
        catalogo.observarProductos()
    ) { entidades, productos ->
        val mapa = productos.associateBy { it.id }
        entidades.mapNotNull { entidad ->
            val producto = mapa[entidad.productoId] ?: return@mapNotNull null
            if (!producto.activo) return@mapNotNull null
            LineaCarrito(producto = producto, cantidad = entidad.cantidad.coerceAtLeast(1))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun agregarAlCarrito(productoId: Long) = withContext(Dispatchers.IO) {
        val producto = catalogo.obtenerPorId(productoId) ?: return@withContext
        if (!producto.activo || producto.agotado) return@withContext
        val actual = carritoDao.obtenerPorProducto(productoId)
        if (actual == null) {
            carritoDao.insertar(CarritoItem(productoId = productoId, cantidad = 1))
        } else if (actual.cantidad < producto.stock) {
            carritoDao.actualizarCantidad(productoId, actual.cantidad + 1)
        }
    }

    suspend fun actualizarCantidadCarrito(productoId: Long, cantidad: Int) = withContext(Dispatchers.IO) {
        val producto = catalogo.obtenerPorId(productoId) ?: return@withContext
        if (!producto.activo || producto.agotado) return@withContext
        val acotada = cantidad.coerceIn(1, producto.stock)
        carritoDao.actualizarCantidad(productoId, acotada)
    }

    suspend fun quitarDelCarrito(productoId: Long) = withContext(Dispatchers.IO) {
        carritoDao.quitarProducto(productoId)
    }
}
