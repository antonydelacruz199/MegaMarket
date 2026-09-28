package com.megamarket.cliente.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CarritoDao {
    @Query("SELECT * FROM carrito ORDER BY id")
    fun observar(): Flow<List<EntidadCarrito>>

    @Query("SELECT * FROM carrito WHERE productoId = :productoId LIMIT 1")
    suspend fun obtenerPorProducto(productoId: Long): EntidadCarrito?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(entidad: EntidadCarrito)

    @Query("UPDATE carrito SET cantidad = :cantidad WHERE productoId = :productoId")
    suspend fun actualizarCantidad(productoId: Long, cantidad: Int)

    @Query("DELETE FROM carrito WHERE productoId = :productoId")
    suspend fun eliminar(productoId: Long)

    @Query("DELETE FROM carrito")
    suspend fun vaciar()
}
