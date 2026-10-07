package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.entities.CarritoEntity
import com.megamarket.cliente.model.LineaCarrito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class CarritoRepository(
    private val dao: CarritoDao,
    private val catalogo: CatalogoRepository
) {
    /**
     * Combina carrito Room + productos Room. Se actualiza cuando cambia el stock cacheado.
     */
    fun observar(): Flow<List<LineaCarrito>> = combine(
        dao.observar(),
        catalogo.observarProductos()
    ) { entidades, productos ->
        val mapa = productos.associateBy { it.id }
        entidades.mapNotNull { entidad ->
            val producto = mapa[entidad.productoId] ?: return@mapNotNull null
            if (!producto.activo) return@mapNotNull null
            LineaCarrito(producto = producto, cantidad = entidad.cantidad.coerceAtLeast(1))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun agregar(productoId: Long) = withContext(Dispatchers.IO) {
        val producto = catalogo.obtenerPorId(productoId) ?: return@withContext
        if (!producto.activo || producto.agotado) return@withContext
        val actual = dao.obtenerPorProducto(productoId)
        if (actual == null) {
            dao.insertar(CarritoEntity(productoId = productoId, cantidad = 1))
        } else if (actual.cantidad < producto.stock) {
            dao.actualizarCantidad(productoId, actual.cantidad + 1)
        }
    }

    suspend fun cambiarCantidad(productoId: Long, cantidad: Int) = withContext(Dispatchers.IO) {
        val producto = catalogo.obtenerPorId(productoId) ?: return@withContext
        if (!producto.activo || producto.agotado) return@withContext
        val acotada = cantidad.coerceIn(1, producto.stock)
        dao.actualizarCantidad(productoId, acotada)
    }

    suspend fun eliminar(productoId: Long) = withContext(Dispatchers.IO) {
        dao.eliminar(productoId)
    }
}
