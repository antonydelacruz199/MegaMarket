package com.megamarket.picker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.picker.data.local.OperacionPickingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperacionPickingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(operacion: OperacionPickingEntity): Long

    @Update
    suspend fun actualizar(operacion: OperacionPickingEntity): Int

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE ownerId = :ownerId AND estado = 'PENDIENTE'
        ORDER BY fechaCreacion ASC, id ASC
        """
    )
    suspend fun pendientesDelUsuario(ownerId: String): List<OperacionPickingEntity>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE uuidOperacion = :uuid
        LIMIT 1
        """
    )
    suspend fun porUuid(uuid: String): OperacionPickingEntity?

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE detalleId = :detalleId
          AND ownerId = :ownerId
          AND estado IN ('PENDIENTE', 'ENVIANDO', 'ERROR')
        ORDER BY fechaCreacion ASC
        """
    )
    suspend fun activasDeDetalle(detalleId: String, ownerId: String): List<OperacionPickingEntity>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE detalleId = :detalleId AND ownerId = :ownerId
        ORDER BY fechaCreacion DESC
        """
    )
    suspend fun deDetalle(detalleId: String, ownerId: String): List<OperacionPickingEntity>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE detalleId = :detalleId AND ownerId = :ownerId
        ORDER BY fechaCreacion DESC
        """
    )
    fun observarDeDetalle(detalleId: String, ownerId: String): Flow<List<OperacionPickingEntity>>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE pedidoId = :pedidoId AND ownerId = :ownerId
        ORDER BY fechaCreacion ASC
        """
    )
    fun observarDePedido(pedidoId: String, ownerId: String): Flow<List<OperacionPickingEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(cantidad), 0) FROM operaciones_pendientes
        WHERE detalleId = :detalleId
          AND ownerId = :ownerId
          AND operacion = 'ESCANEAR'
          AND estado IN ('PENDIENTE', 'ENVIANDO')
        """
    )
    suspend fun cantidadPendienteEscaneos(detalleId: String, ownerId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM operaciones_pendientes
        WHERE detalleId = :detalleId
          AND ownerId = :ownerId
          AND operacion = 'MARCAR_FALTANTE'
          AND estado IN ('PENDIENTE', 'ENVIANDO')
        """
    )
    suspend fun faltantesActivos(detalleId: String, ownerId: String): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'PENDIENTE', fechaActualizacion = :ahora
        WHERE estado = 'ENVIANDO'
        """
    )
    suspend fun recuperarEnviandoAtascadas(ahora: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'ENVIANDO', fechaActualizacion = :ahora
        WHERE id = :id AND ownerId = :ownerId AND estado IN ('PENDIENTE', 'ERROR')
        """
    )
    suspend fun marcarEnviando(id: Long, ownerId: String, ahora: Long): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'SINCRONIZADO',
            resultado = :resultado,
            sincronizadoEn = :ahora,
            fechaActualizacion = :ahora,
            ultimoError = NULL
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun marcarSincronizada(
        id: Long,
        ownerId: String,
        resultado: String?,
        ahora: Long
    ): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'ERROR',
            resultado = :codigo,
            ultimoError = :error,
            intentos = intentos + 1,
            fechaActualizacion = :ahora
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun marcarError(
        id: Long,
        ownerId: String,
        error: String,
        ahora: Long,
        codigo: String? = null
    ): Int

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = 'PENDIENTE',
            ultimoError = :error,
            fechaActualizacion = :ahora
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun devolverAPendiente(id: Long, ownerId: String, error: String?, ahora: Long): Int
}
