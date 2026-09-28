package com.megamarket.cliente.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EntidadCarrito::class, EntidadFavorito::class, EntidadCliente::class],
    version = 2,
    exportSchema = false
)
abstract class BaseDatosCliente : RoomDatabase() {
    abstract fun carritoDao(): CarritoDao
    abstract fun favoritoDao(): FavoritoDao
    abstract fun clienteDao(): ClienteDao

    companion object {
        @Volatile
        private var instancia: BaseDatosCliente? = null

        fun obtener(contexto: Context): BaseDatosCliente {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    BaseDatosCliente::class.java,
                    "megamarket_cliente.db"
                ).fallbackToDestructiveMigration().build().also { instancia = it }
            }
        }
    }
}
