package com.megamarket.app.data.repository

import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.entities.CategoriaEntity
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.Categoria
import com.megamarket.modelo.CategoriasBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CategoriaRepository(
    private val dao: CategoriaDao
) {
    fun observarActivas(): Flow<List<Categoria>> =
        dao.observarActivas().map { lista -> lista.map { it.toModel() } }

    suspend fun obtenerActivas(): List<Categoria> = withContext(Dispatchers.IO) {
        asegurarBootstrap()
        dao.obtenerActivas().map { it.toModel() }
    }

    suspend fun obtenerPorId(id: Long): Categoria? = withContext(Dispatchers.IO) {
        dao.obtenerPorId(id)?.toModel()
    }

    /** Inserta las 6 categorías locales una sola vez. remoteId queda null. */
    suspend fun asegurarBootstrap() = withContext(Dispatchers.IO) {
        if (dao.contar() > 0) return@withContext
        dao.insertarVarias(
            CategoriasBootstrap.NOMBRES.map { nombre ->
                CategoriaEntity(nombre = nombre)
            }
        )
    }
}
