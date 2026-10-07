package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity

@Dao
interface PedidoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarPedido(pedido: PedidoEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarDetalles(detalles: List<PedidoDetalleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarDireccion(direccion: DireccionEntity): Long

    @Query("SELECT * FROM pedidos WHERE id = :pedidoId LIMIT 1")
    suspend fun obtenerPedidoPorId(pedidoId: Long): PedidoEntity?

    @Query("SELECT * FROM pedido_detalle WHERE pedidoId = :pedidoId ORDER BY id")
    suspend fun obtenerDetallesPedido(pedidoId: Long): List<PedidoDetalleEntity>

    @Query("SELECT * FROM direcciones WHERE pedidoId = :pedidoId LIMIT 1")
    suspend fun obtenerDireccionPedido(pedidoId: Long): DireccionEntity?
}
