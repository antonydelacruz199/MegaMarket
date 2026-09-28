package com.megamarket.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.local.dao.ProductoDao

/**
 * Catálogo privado del administrador. El cliente no abre este archivo:
 * lo consulta por el ContentProvider.
 */
@Database(
    entities = [EntidadProducto::class, EntidadAdministrador::class],
    version = 2,
    exportSchema = false
)
abstract class BaseDatosMegaMarket : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun administradorDao(): AdministradorDao

    companion object {
        private const val NOMBRE = "megamarket_catalogo.db"

        @Volatile
        private var instancia: BaseDatosMegaMarket? = null

        fun obtener(contexto: Context): BaseDatosMegaMarket {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    BaseDatosMegaMarket::class.java,
                    NOMBRE
                ).fallbackToDestructiveMigration().build().also { instancia = it }
            }
        }
    }
}
