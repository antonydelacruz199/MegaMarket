package com.megamarket.cliente.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritoDao {
    @Query("SELECT * FROM favoritos ORDER BY productoId")
    fun observar(): Flow<List<EntidadFavorito>>

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE productoId = :productoId)")
    fun observarExiste(productoId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: EntidadFavorito)

    @Query("DELETE FROM favoritos WHERE productoId = :productoId")
    suspend fun eliminar(productoId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE productoId = :productoId)")
    suspend fun existe(productoId: Long): Boolean
}
