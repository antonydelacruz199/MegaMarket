package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.megamarket.app.data.local.entities.ProductoEntity

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos WHERE trim(nombre) != '' ORDER BY nombre COLLATE NOCASE")
    fun obtenerTodos(): List<ProductoEntity>

    @Query("SELECT * FROM productos WHERE activo = 1 AND trim(nombre) != '' ORDER BY nombre COLLATE NOCASE")
    fun obtenerActivos(): List<ProductoEntity>

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    fun obtenerPorId(id: Long): ProductoEntity?

    @Insert
    suspend fun insertar(entidad: ProductoEntity): Long

    @Update
    suspend fun actualizar(entidad: ProductoEntity): Int

    @Query("DELETE FROM productos WHERE trim(nombre) = ''")
    suspend fun eliminarSinDatos()

    /**
     * Descuenta stock de forma atómica. Devuelve 1 si se aplicó; 0 si el producto
     * no existe, está inactivo o no hay unidades suficientes (nunca deja stock negativo).
     */
    @Query(
        """
        UPDATE productos
        SET stock = stock - :cantidad
        WHERE id = :productoId
          AND activo = 1
          AND stock >= :cantidad
        """
    )
    fun descontarStock(productoId: Long, cantidad: Int): Int

    /** Compensación interna tras un descuento ya aplicado. No es un CRUD genérico. */
    @Query(
        """
        UPDATE productos
        SET stock = stock + :cantidad
        WHERE id = :productoId
          AND :cantidad > 0
        """
    )
    fun incrementarStock(productoId: Long, cantidad: Int): Int
}
