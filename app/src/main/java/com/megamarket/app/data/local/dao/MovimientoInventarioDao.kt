package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.megamarket.app.data.local.entities.MovimientoInventarioEntity

@Dao
interface MovimientoInventarioDao {
    @Insert
    suspend fun insertar(entidad: MovimientoInventarioEntity): Long

    @Query("SELECT * FROM movimientos_inventario WHERE uuidOperacion = :uuid LIMIT 1")
    suspend fun obtenerPorUuid(uuid: String): MovimientoInventarioEntity?
}
