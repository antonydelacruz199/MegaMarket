package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.megamarket.app.data.local.Administrador

@Dao
interface AdministradorDao {
    @Query("SELECT * FROM administradores WHERE usuario = :usuario LIMIT 1")
    suspend fun buscarPorUsuario(usuario: String): Administrador?

    @Query("SELECT COUNT(*) FROM administradores")
    suspend fun contar(): Int

    @Insert
    suspend fun insertar(entidad: Administrador): Long

    @Update
    suspend fun actualizarAdministrador(entidad: Administrador): Int
}
