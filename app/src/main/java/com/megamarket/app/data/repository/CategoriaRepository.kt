package com.megamarket.app.data.repository

import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.Categoria
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
        categoriaDao.obtenerActivas().map { it.toModel() }
    }

    suspend fun obtenerPorId(id: Long): Categoria? = withContext(Dispatchers.IO) {
        categoriaDao.obtenerPorId(id)?.toModel()
    }
}
