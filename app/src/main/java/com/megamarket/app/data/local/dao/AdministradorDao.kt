package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.megamarket.app.data.local.EntidadAdministrador

@Dao
interface AdministradorDao {
    @Query("SELECT * FROM administradores WHERE usuario = :usuario LIMIT 1")
    suspend fun buscarPorUsuario(usuario: String): EntidadAdministrador?

    @Query("SELECT COUNT(*) FROM administradores")
    suspend fun contar(): Int

    @Insert
    suspend fun insertar(entidad: EntidadAdministrador): Long
}
