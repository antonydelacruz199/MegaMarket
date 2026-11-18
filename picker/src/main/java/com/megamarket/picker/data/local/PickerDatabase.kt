package com.megamarket.picker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.megamarket.picker.data.local.dao.LineaPickingDao
import com.megamarket.picker.data.local.dao.OperacionPickingDao
import com.megamarket.picker.data.local.dao.PedidoPickingDao

@Database(
    entities = [
        PedidoPickingEntity::class,
        LineaPickingEntity::class,
        OperacionPickingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PickerDatabase : RoomDatabase() {
    abstract fun pedidoDao(): PedidoPickingDao
    abstract fun lineaDao(): LineaPickingDao
    abstract fun operacionDao(): OperacionPickingDao

    companion object {
        private const val NOMBRE = "megamarket_picker.db"

        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE lineas_picking ADD COLUMN resolubleSinProducto INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE lineas_picking ADD COLUMN sustitucionAceptadaId TEXT"
                )
                db.execSQL(
                    "ALTER TABLE lineas_picking ADD COLUMN productoSustitutoId TEXT"
                )
                db.execSQL(
                    "ALTER TABLE lineas_picking ADD COLUMN nombreSustituto TEXT"
                )
            }
        }

        @Volatile
        private var instancia: PickerDatabase? = null

        fun obtener(contexto: Context): PickerDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    PickerDatabase::class.java,
                    NOMBRE
                )
                    .addMigrations(MIGRACION_1_2, MIGRACION_2_3)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
