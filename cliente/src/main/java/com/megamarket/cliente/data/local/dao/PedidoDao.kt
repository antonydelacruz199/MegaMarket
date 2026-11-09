package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.megamarket.cliente.data.local.Direccion
import com.megamarket.cliente.data.local.PedidoDetalle
import com.megamarket.cliente.data.local.Pedido

@Dao
interface PedidoDao {
    @Query("SELECT * FROM pedidos WHERE clienteId=:ownerId ORDER BY fecha DESC")
    fun observarDelUsuario(ownerId: Long): kotlinx.coroutines.flow.Flow<List<Pedido>>

    @Query("SELECT * FROM pedidos WHERE remote_id IS NULL AND estado IN ('PENDIENTE_CONFIRMACION','REQUIERE_REVISION','CONFIRMADO')")
    suspend fun reservasPendientes(): List<Pedido>

    @Update
    suspend fun actualizarDetalle(detalle: PedidoDetalle): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarPedido(pedido: Pedido): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarDetalles(detalles: List<PedidoDetalle>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarDireccion(direccion: Direccion): Long

    @Update
    suspend fun actualizarPedido(pedido: Pedido): Int

    @Query("SELECT * FROM pedidos WHERE id = :pedidoId LIMIT 1")
    suspend fun obtenerPedidoPorId(pedidoId: Long): Pedido?

    @Query("SELECT * FROM pedido_detalle WHERE pedidoId = :pedidoId ORDER BY id")
    suspend fun obtenerDetallesPedido(pedidoId: Long): List<PedidoDetalle>

    @Query("SELECT * FROM direcciones WHERE pedidoId = :pedidoId LIMIT 1")
    suspend fun obtenerDireccionPedido(pedidoId: Long): Direccion?
}
