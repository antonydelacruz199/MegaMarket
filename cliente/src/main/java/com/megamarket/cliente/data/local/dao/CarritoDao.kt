package com.megamarket.cliente.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.cliente.data.local.entities.CarritoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CarritoDao {
    @Query("SELECT * FROM carrito ORDER BY id")
    fun observar(): Flow<List<CarritoEntity>>

    @Query("SELECT * FROM carrito ORDER BY id")
    suspend fun obtenerTodos(): List<CarritoEntity>

    @Query("SELECT * FROM carrito WHERE productoId = :productoId LIMIT 1")
    suspend fun obtenerPorProducto(productoId: Long): CarritoEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(entidad: CarritoEntity)

    @Query("UPDATE carrito SET cantidad = :cantidad WHERE productoId = :productoId")
    suspend fun actualizarCantidad(productoId: Long, cantidad: Int)

    @Query("DELETE FROM carrito WHERE productoId = :productoId")
    suspend fun eliminar(productoId: Long)

    @Query("DELETE FROM carrito WHERE productoId IN (:productoIds)")
    suspend fun eliminarVarios(productoIds: List<Long>)

    @Query("DELETE FROM carrito")
    suspend fun vaciar()
}
