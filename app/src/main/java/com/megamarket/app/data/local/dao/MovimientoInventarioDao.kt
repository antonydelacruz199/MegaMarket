package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.megamarket.app.data.local.MovimientoInventario

@Dao
interface MovimientoInventarioDao {
    @Insert
    suspend fun insertar(entidad: MovimientoInventario): Long

    @Update
    suspend fun actualizarMovimientoInventario(entidad: MovimientoInventario): Int

    @Query("SELECT * FROM movimientos_inventario WHERE uuidOperacion = :uuid LIMIT 1")
    suspend fun obtenerPorUuid(uuid: String): MovimientoInventario?
}
