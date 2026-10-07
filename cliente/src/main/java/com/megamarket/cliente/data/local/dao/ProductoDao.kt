package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
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

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Long): ProductoEntity?

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    fun observarPorId(id: Long): Flow<ProductoEntity?>

    @Query("SELECT * FROM productos WHERE provider_id = :providerId LIMIT 1")
    suspend fun obtenerPorProviderId(providerId: Long): ProductoEntity?

    @Query("SELECT * FROM productos WHERE remote_id = :remoteId LIMIT 1")
    suspend fun obtenerPorRemoteId(remoteId: String): ProductoEntity?

    @Insert
    suspend fun insertar(entidad: ProductoEntity): Long

    @Update
    suspend fun actualizar(entidad: ProductoEntity): Int
}
