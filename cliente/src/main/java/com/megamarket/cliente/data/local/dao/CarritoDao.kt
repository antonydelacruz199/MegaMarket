package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.cliente.data.local.CarritoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CarritoDao {
    @Query("SELECT * FROM carrito WHERE ownerId > 0")
    suspend fun todasLasReservas(): List<CarritoItem>

    @Query("SELECT * FROM carrito WHERE ownerId = :ownerId ORDER BY id")
    fun observarItems(ownerId: Long = 0): Flow<List<CarritoItem>>

    @Query("SELECT * FROM carrito WHERE ownerId = :ownerId ORDER BY id")
    suspend fun obtenerTodos(ownerId: Long = 0): List<CarritoItem>

    @Query("SELECT * FROM carrito WHERE ownerId = :ownerId AND productoId = :productoId LIMIT 1")
    suspend fun obtenerPorProducto(productoId: Long, ownerId: Long = 0): CarritoItem?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(entidad: CarritoItem)

    @Query("UPDATE carrito SET cantidad = :cantidad WHERE ownerId = :ownerId AND productoId = :productoId")
    suspend fun actualizarCantidad(productoId: Long, cantidad: Int, ownerId: Long = 0)

    @Query("DELETE FROM carrito WHERE ownerId = :ownerId AND productoId = :productoId")
    suspend fun quitarProducto(productoId: Long, ownerId: Long = 0)

    @Query("DELETE FROM carrito WHERE ownerId = :ownerId AND productoId IN (:productoIds)")
    suspend fun quitarProductos(productoIds: List<Long>, ownerId: Long = 0)

    @Query("DELETE FROM carrito WHERE ownerId = :ownerId")
    suspend fun vaciarCarrito(ownerId: Long = 0)
}
