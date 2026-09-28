package com.megamarket.cliente.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes WHERE usuario = :usuario LIMIT 1")
    suspend fun buscarPorUsuario(usuario: String): EntidadCliente?

    @Query("SELECT COUNT(*) FROM clientes")
    suspend fun contar(): Int

    @Insert
    suspend fun insertar(entidad: EntidadCliente): Long
}
