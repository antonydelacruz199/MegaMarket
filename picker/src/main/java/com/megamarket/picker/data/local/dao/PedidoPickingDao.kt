package com.megamarket.picker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.picker.data.local.PedidoPickingEntity

@Dao
interface PedidoPickingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(pedido: PedidoPickingEntity)

    @Query("SELECT * FROM pedidos_picking WHERE id = :id AND ownerId = :ownerId LIMIT 1")
    suspend fun obtener(id: String, ownerId: String): PedidoPickingEntity?

    @Query("DELETE FROM pedidos_picking WHERE id = :id AND ownerId = :ownerId")
    suspend fun eliminar(id: String, ownerId: String)
}
