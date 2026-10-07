package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.entities.CarritoEntity
import com.megamarket.cliente.model.LineaCarrito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CarritoRepository(
    private val dao: CarritoDao,
    private val catalogo: CatalogoRepository
) {
    /**
     * Muestra la cantidad guardada tal cual, aunque el stock haya bajado, para que el
     * cliente vea el exceso y lo corrija; el checkout rechaza las cantidades que no alcanzan.
     */
    fun observar(): Flow<List<LineaCarrito>> = dao.observar().map { entidades ->
        val productos = catalogo.leerActivos().associateBy { it.id }
        entidades.mapNotNull { entidad ->
            val producto = productos[entidad.productoId] ?: return@mapNotNull null
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
