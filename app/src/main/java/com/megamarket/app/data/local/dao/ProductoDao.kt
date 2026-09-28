package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.megamarket.app.data.local.EntidadProducto

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos ORDER BY nombre COLLATE NOCASE")
    fun obtenerTodos(): List<EntidadProducto>

    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre COLLATE NOCASE")
    fun obtenerActivos(): List<EntidadProducto>

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    fun obtenerPorId(id: Long): EntidadProducto?

    @Insert
    suspend fun insertar(entidad: EntidadProducto): Long
}
