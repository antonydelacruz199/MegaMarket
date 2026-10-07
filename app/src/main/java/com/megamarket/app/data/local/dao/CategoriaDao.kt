package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.app.data.local.entities.CategoriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {
    @Query(
        """
        SELECT * FROM categorias
        WHERE remote_deleted_at IS NULL
        ORDER BY nombre COLLATE NOCASE
        """
    )
    fun observarActivas(): Flow<List<CategoriaEntity>>

    @Query(
        """
        SELECT * FROM categorias
        WHERE remote_deleted_at IS NULL
        ORDER BY nombre COLLATE NOCASE
        """
    )
    suspend fun obtenerActivas(): List<CategoriaEntity>

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contar(): Int

    @Query("SELECT * FROM categorias WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Long): CategoriaEntity?

    @Query("SELECT * FROM categorias WHERE remote_id = :remoteId LIMIT 1")
    suspend fun obtenerPorRemoteId(remoteId: String): CategoriaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: CategoriaEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarVarias(entidades: List<CategoriaEntity>): List<Long>

    @Update
    suspend fun actualizar(entidad: CategoriaEntity): Int

    @Query(
        """
        INSERT INTO categorias (nombre, remote_id, remote_version, remote_updated_at, remote_deleted_at)
        VALUES (:nombre, NULL, NULL, NULL, NULL)
        """
    )
    suspend fun insertarNombreSiNoExiste(nombre: String): Long
}
