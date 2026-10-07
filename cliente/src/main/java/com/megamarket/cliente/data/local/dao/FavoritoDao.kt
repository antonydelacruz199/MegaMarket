package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.cliente.data.local.entities.FavoritoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritoDao {
    @Query("SELECT * FROM favoritos ORDER BY productoId")
    fun observar(): Flow<List<FavoritoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE productoId = :productoId)")
    fun observarExiste(productoId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: FavoritoEntity)

    @Query("DELETE FROM favoritos WHERE productoId = :productoId")
    suspend fun eliminar(productoId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE productoId = :productoId)")
    suspend fun existe(productoId: Long): Boolean
}
