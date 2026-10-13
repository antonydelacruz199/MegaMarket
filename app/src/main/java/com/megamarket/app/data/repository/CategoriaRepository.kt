package com.megamarket.app.data.repository

import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.Categoria as CategoriaRoom
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.Categoria
import com.megamarket.modelo.CategoriasBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CategoriaRepository(
    private val categoriaDao: CategoriaDao
) {
    fun observarActivas(): Flow<List<Categoria>> =
        categoriaDao.observarActivas().map { lista -> lista.map { it.toModel() } }

    suspend fun obtenerActivas(): List<Categoria> = withContext(Dispatchers.IO) {
        asegurarBootstrap()
        categoriaDao.obtenerActivas().map { it.toModel() }
    }

    suspend fun obtenerPorId(id: Long): Categoria? = withContext(Dispatchers.IO) {
        categoriaDao.obtenerPorId(id)?.toModel()
    }

    /** Inserta las 6 categorías locales una sola vez. remoteId queda null. */
    suspend fun asegurarBootstrap() = withContext(Dispatchers.IO) {
        if (categoriaDao.contar() > 0) return@withContext
        categoriaDao.insertarVarias(
            CategoriasBootstrap.NOMBRES.map { nombre ->
                CategoriaRoom(nombre = nombre)
            }
        )
    }
}
