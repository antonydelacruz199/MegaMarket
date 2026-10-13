package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.cliente.data.local.CarritoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CarritoDao {
    @Query("SELECT * FROM carrito ORDER BY id")
    fun observarItems(): Flow<List<CarritoItem>>

    @Query("SELECT * FROM carrito ORDER BY id")
    suspend fun obtenerTodos(): List<CarritoItem>

    @Query("SELECT * FROM carrito WHERE productoId = :productoId LIMIT 1")
    suspend fun obtenerPorProducto(productoId: Long): CarritoItem?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(entidad: CarritoItem)

    @Query("UPDATE carrito SET cantidad = :cantidad WHERE productoId = :productoId")
    suspend fun actualizarCantidad(productoId: Long, cantidad: Int)

    @Query("DELETE FROM carrito WHERE productoId = :productoId")
    suspend fun quitarProducto(productoId: Long)

    @Query("DELETE FROM carrito WHERE productoId IN (:productoIds)")
    suspend fun quitarProductos(productoIds: List<Long>)

    @Query("DELETE FROM carrito")
    suspend fun vaciarCarrito()
}
