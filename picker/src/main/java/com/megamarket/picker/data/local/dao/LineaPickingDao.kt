package com.megamarket.picker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.picker.data.local.LineaPickingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LineaPickingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodas(lineas: List<LineaPickingEntity>)

    @Query(
        """
        SELECT * FROM lineas_picking
        WHERE pedidoId = :pedidoId AND ownerId = :ownerId
        ORDER BY pasillo ASC, nombre ASC, id ASC
        """
    )
    suspend fun dePedido(pedidoId: String, ownerId: String): List<LineaPickingEntity>

    @Query(
        """
        SELECT * FROM lineas_picking
        WHERE pedidoId = :pedidoId AND ownerId = :ownerId
        ORDER BY pasillo ASC, nombre ASC, id ASC
        """
    )
    fun observarDePedido(pedidoId: String, ownerId: String): Flow<List<LineaPickingEntity>>

    @Query(
        """
        SELECT * FROM lineas_picking
        WHERE id = :id AND ownerId = :ownerId
        LIMIT 1
        """
    )
    suspend fun obtener(id: String, ownerId: String): LineaPickingEntity?

    @Query(
        """
        UPDATE lineas_picking
        SET cantidadArmada = :armada,
            estadoPicking = :estado,
            actualizadoEn = :ahora
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun actualizarConfirmada(
        id: String,
        ownerId: String,
        armada: Int,
        estado: String,
        ahora: Long
    ): Int

    @Query("DELETE FROM lineas_picking WHERE pedidoId = :pedidoId AND ownerId = :ownerId")
    suspend fun eliminarDePedido(pedidoId: String, ownerId: String)
}
