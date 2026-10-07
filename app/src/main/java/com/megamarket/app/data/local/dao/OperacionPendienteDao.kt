package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.app.data.local.entities.OperacionPendienteEntity
import kotlinx.coroutines.flow.Flow

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
    suspend fun obtenerParaEnviar(): List<OperacionPendienteEntity>

    @Query(
        """
        SELECT COUNT(*) FROM operaciones_pendientes
        WHERE estado IN ('PENDIENTE', 'ENVIANDO', 'ERROR', 'SINCRONIZANDO')
        """
    )
    fun observarPendientesActivos(): Flow<Int>

    @Query("SELECT COUNT(*) FROM operaciones_pendientes WHERE estado = 'ERROR'")
    fun observarErrores(): Flow<Int>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE tipoEntidad = :tipoEntidad
          AND entidadIdLocal = :entidadIdLocal
          AND operacion = :operacion
          AND estado IN ('PENDIENTE', 'ENVIANDO', 'ERROR', 'SINCRONIZANDO')
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
        SET estado = 'PENDIENTE', fechaActualizacion = :ahora
        WHERE estado IN ('ENVIANDO', 'SINCRONIZANDO')
        """
    )
    suspend fun recuperarEnviandoAtascadas(ahora: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'ENVIANDO', fechaActualizacion = :ahora
        WHERE id = :id
        """
    )
    suspend fun marcarEnviando(id: Long, ahora: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'SINCRONIZADO',
            sincronizadoEn = :ahora,
            fechaActualizacion = :ahora,
            ultimoError = NULL
        WHERE id = :id
        """
    )
    suspend fun marcarSincronizado(id: Long, ahora: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'ERROR',
            ultimoError = :error,
            intentos = intentos + 1,
            fechaActualizacion = :ahora
        WHERE id = :id
        """
    )
    suspend fun marcarError(id: Long, error: String, ahora: Long): Int

    @Query(
        """
        DELETE FROM operaciones_pendientes
        WHERE tipoEntidad = :tipoEntidad
          AND entidadIdLocal = :entidadIdLocal
          AND operacion = :operacion
          AND estado IN ('PENDIENTE', 'ERROR', 'ENVIANDO', 'SINCRONIZANDO')
        """
    )
    suspend fun eliminarActivasDe(
        tipoEntidad: String,
        entidadIdLocal: Long,
        operacion: String
    ): Int
}
