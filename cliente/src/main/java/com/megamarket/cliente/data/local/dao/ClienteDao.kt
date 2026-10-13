package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.megamarket.cliente.data.local.Cliente

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes WHERE usuario = :usuario LIMIT 1")
    suspend fun buscarPorUsuario(usuario: String): Cliente?

    @Query("SELECT COUNT(*) FROM clientes")
    suspend fun contar(): Int

    @Insert
    suspend fun insertar(entidad: Cliente): Long

    @Update
    suspend fun actualizarCliente(entidad: Cliente): Int
}
