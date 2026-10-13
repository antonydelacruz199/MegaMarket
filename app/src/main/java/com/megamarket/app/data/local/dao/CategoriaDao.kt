package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.app.data.local.Categoria
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
    fun observarActivas(): Flow<List<Categoria>>

    @Query(
        """
        SELECT * FROM categorias
        WHERE remote_deleted_at IS NULL
        ORDER BY nombre COLLATE NOCASE
        """
    )
    suspend fun obtenerActivas(): List<Categoria>

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contar(): Int

    @Query("SELECT * FROM categorias WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Long): Categoria?

    @Query("SELECT * FROM categorias WHERE remote_id = :remoteId LIMIT 1")
    suspend fun obtenerPorRemoteId(remoteId: String): Categoria?

    @Query("SELECT * FROM categorias WHERE nombre = :nombre COLLATE NOCASE LIMIT 1")
    suspend fun obtenerPorNombre(nombre: String): Categoria?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: Categoria): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarVarias(entidades: List<Categoria>): List<Long>

    @Update
    suspend fun actualizarCategoria(entidad: Categoria): Int

    @Query(
        """
        INSERT INTO categorias (nombre, remote_id, remote_version, remote_updated_at, remote_deleted_at)
        VALUES (:nombre, NULL, NULL, NULL, NULL)
        """
    )
    suspend fun insertarNombreSiNoExiste(nombre: String): Long
}
