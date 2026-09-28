package com.megamarket.app.data.repositorio

import com.megamarket.app.data.local.aEntidad
import com.megamarket.app.data.local.aProducto
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepositorioProducto(
    private val dao: ProductoDao
) {
    suspend fun obtenerTodos(): List<Producto> = withContext(Dispatchers.IO) {
        dao.obtenerTodos().map { it.aProducto() }
    }

    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        dao.obtenerActivos().map { it.aProducto() }
    }

    suspend fun insertar(producto: Producto): Long = withContext(Dispatchers.IO) {
        dao.insertar(producto.aEntidad())
    }
}
