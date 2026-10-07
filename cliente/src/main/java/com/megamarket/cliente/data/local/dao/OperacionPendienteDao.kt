package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity

@Dao
interface OperacionPendienteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(entidad: OperacionPendienteEntity): Long

    @Update
    suspend fun actualizar(entidad: OperacionPendienteEntity): Int

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE estado IN ('PENDIENTE', 'ERROR')
        ORDER BY fechaCreacion ASC
        """
    )
    suspend fun obtenerPendientes(): List<OperacionPendienteEntity>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE tipoEntidad = :tipoEntidad
          AND entidadIdLocal = :entidadIdLocal
          AND operacion = :operacion
          AND estado IN ('PENDIENTE', 'ERROR')
        LIMIT 1
        """
    )
    suspend fun obtenerPendienteDe(
        tipoEntidad: String,
        entidadIdLocal: Long,
        operacion: String
    ): OperacionPendienteEntity?

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'SINCRONIZANDO'
        WHERE id = :id
        """
    )
    suspend fun marcarSincronizando(id: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'ERROR', ultimoError = :error, intentos = intentos + 1
        WHERE id = :id
        """
    )
    suspend fun marcarError(id: Long, error: String): Int

    @Query("DELETE FROM operaciones_pendientes WHERE id = :id")
    suspend fun eliminar(id: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET intentos = intentos + 1
        WHERE id = :id
        """
    )
    suspend fun incrementarIntentos(id: Long): Int
}
