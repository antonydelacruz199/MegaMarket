package com.megamarket.cliente.data.repositorio

import com.megamarket.cliente.data.local.CarritoDao
import com.megamarket.cliente.data.local.EntidadCarrito
import com.megamarket.cliente.modelo.LineaCarrito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RepositorioCarrito(
    private val dao: CarritoDao,
    private val catalogo: RepositorioCatalogo
) {
    fun observar(): Flow<List<LineaCarrito>> = dao.observar().map { entidades ->
        val productos = catalogo.leerActivos().associateBy { it.id }
        entidades.mapNotNull { entidad ->
            val producto = productos[entidad.productoId] ?: return@mapNotNull null
            if (!producto.activo) return@mapNotNull null
            val cantidad = if (producto.agotado) {
                entidad.cantidad.coerceAtLeast(1)
            } else {
                entidad.cantidad.coerceIn(1, producto.stock)
            }
            LineaCarrito(producto = producto, cantidad = cantidad)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun agregar(productoId: Long) = withContext(Dispatchers.IO) {
        val producto = catalogo.obtenerPorId(productoId) ?: return@withContext
        if (!producto.activo || producto.agotado) return@withContext
        val actual = dao.obtenerPorProducto(productoId)
        if (actual == null) {
            dao.insertar(EntidadCarrito(productoId = productoId, cantidad = 1))
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

    suspend fun vaciar() = withContext(Dispatchers.IO) {
        dao.vaciar()
    }
}
