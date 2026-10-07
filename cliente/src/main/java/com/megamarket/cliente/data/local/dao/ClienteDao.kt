package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.megamarket.cliente.data.local.entities.ClienteEntity

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes WHERE usuario = :usuario LIMIT 1")
    suspend fun buscarPorUsuario(usuario: String): ClienteEntity?

    @Query("SELECT COUNT(*) FROM clientes")
    suspend fun contar(): Int

    @Insert
    suspend fun insertar(entidad: ClienteEntity): Long

    @Update
    suspend fun actualizar(entidad: ClienteEntity): Int
}
