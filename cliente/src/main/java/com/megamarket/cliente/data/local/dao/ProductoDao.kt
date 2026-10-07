package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.cliente.data.local.entities.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query(
        """
        SELECT * FROM productos
        WHERE activo = 1
          AND trim(nombre) != ''
          AND remote_deleted_at IS NULL
        ORDER BY nombre COLLATE NOCASE
        """
    )
    fun observarActivos(): Flow<List<ProductoEntity>>

    @Query(
        """
        SELECT * FROM productos
        WHERE activo = 1
          AND trim(nombre) != ''
          AND remote_deleted_at IS NULL
        ORDER BY nombre COLLATE NOCASE
        """
    )
    suspend fun obtenerActivos(): List<ProductoEntity>

    @Query("SELECT COUNT(*) FROM productos")
    suspend fun contar(): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM productos")
    suspend fun maxId(): Long

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Long): ProductoEntity?

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    fun observarPorId(id: Long): Flow<ProductoEntity?>

    @Query("SELECT * FROM productos WHERE remote_id = :remoteId LIMIT 1")
    suspend fun obtenerPorRemoteId(remoteId: String): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entidad: ProductoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVarios(entidades: List<ProductoEntity>)

    @Update
    suspend fun actualizar(entidad: ProductoEntity): Int

    @Query(
        """
        UPDATE productos
        SET stock = :stock,
            remote_id = COALESCE(:remoteId, remote_id),
            remote_version = COALESCE(:remoteVersion, remote_version),
            remote_updated_at = COALESCE(:remoteUpdatedAt, remote_updated_at),
            remote_deleted_at = :remoteDeletedAt
        WHERE id = :id
        """
    )
    suspend fun actualizarDesdeCatalogo(
        id: Long,
        stock: Int,
        remoteId: String?,
        remoteVersion: Long?,
        remoteUpdatedAt: String?,
        remoteDeletedAt: String?
    ): Int
}
