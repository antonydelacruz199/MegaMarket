package com.megamarket.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.entities.AdministradorEntity
import com.megamarket.app.data.local.entities.ProductoEntity

/**
 * Catálogo privado del administrador. El cliente no abre este archivo:
 * lo consulta por el ContentProvider.
 */
@Database(
    entities = [ProductoEntity::class, AdministradorEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun administradorDao(): AdministradorDao

    companion object {
        private const val NOMBRE = "megamarket_catalogo.db"

        @Volatile
        private var instancia: AppDatabase? = null

        fun getInstance(contexto: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    AppDatabase::class.java,
                    NOMBRE
                )
                    // La versión 1 fue un prototipo sin migración conocida.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
