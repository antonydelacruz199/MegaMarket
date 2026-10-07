package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.megamarket.cliente.data.local.entities.MovimientoInventarioEntity

@Dao
interface MovimientoInventarioDao {
    @Insert
    suspend fun insertar(entidad: MovimientoInventarioEntity): Long

    @Update
    suspend fun actualizar(entidad: MovimientoInventarioEntity): Int

    @Query("SELECT * FROM movimientos_inventario WHERE uuidOperacion = :uuid LIMIT 1")
    suspend fun obtenerPorUuid(uuid: String): MovimientoInventarioEntity?

    @Query(
        """
        SELECT COUNT(*) FROM movimientos_inventario m
        INNER JOIN operaciones_pendientes o ON o.uuidOperacion = m.uuidOperacion
        WHERE m.productoId = :productoId
          AND o.estado IN ('PENDIENTE', 'ENVIANDO', 'ERROR', 'SINCRONIZANDO')
        """
    )
    suspend fun contarPendientesDeProducto(productoId: Long): Int
}
