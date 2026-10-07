package com.megamarket.app.data.repository

import android.content.ContentResolver
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.mapper.toEntity
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductoRepository(
    private val dao: ProductoDao,
    private val resolver: ContentResolver
) {
    suspend fun obtenerTodos(): List<Producto> = withContext(Dispatchers.IO) {
        dao.eliminarSinDatos()
        dao.obtenerTodos().map { it.toModel() }
    }

    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        dao.eliminarSinDatos()
        dao.obtenerActivos().map { it.toModel() }
    }

    suspend fun obtenerPorId(id: Long): Producto? = withContext(Dispatchers.IO) {
        dao.obtenerPorId(id)?.toModel()?.takeIf { it.nombre.isNotBlank() }
    }

    suspend fun insertar(producto: Producto): Long = withContext(Dispatchers.IO) {
        val id = dao.insertar(producto.toEntity())
        notificarCambio(id)
        id
    }

    /** Devuelve false si el producto ya no existe. */
    suspend fun actualizar(producto: Producto): Boolean = withContext(Dispatchers.IO) {
        val filas = dao.actualizar(producto.toEntity())
        if (filas > 0) notificarCambio(producto.id)
        filas > 0
    }

    /** Avisa al cliente que el catálogo publicado por el ContentProvider cambió. */
    private fun notificarCambio(id: Long) {
        resolver.notifyChange(ContratoCatalogo.URI_PRODUCTOS, null)
        resolver.notifyChange(ContratoCatalogo.uriProducto(id), null)
    }
}
