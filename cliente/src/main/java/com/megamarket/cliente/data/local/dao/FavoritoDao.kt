package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.cliente.data.local.Favorito
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritoDao {
    @Query("SELECT * FROM favoritos WHERE ownerId = :ownerId ORDER BY productoId")
    fun observarFavoritos(ownerId: Long = 0): Flow<List<Favorito>>

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE ownerId = :ownerId AND productoId = :productoId)")
    fun observarExiste(productoId: Long, ownerId: Long = 0): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: Favorito)

    @Query("DELETE FROM favoritos WHERE ownerId = :ownerId AND productoId = :productoId")
    suspend fun quitarFavorito(productoId: Long, ownerId: Long = 0)

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE ownerId = :ownerId AND productoId = :productoId)")
    suspend fun existe(productoId: Long, ownerId: Long = 0): Boolean
}
