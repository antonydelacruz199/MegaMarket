package com.megamarket.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.entities.AdministradorEntity
import com.megamarket.app.data.local.entities.CategoriaEntity
import com.megamarket.app.data.local.entities.ProductoEntity
import com.megamarket.modelo.CategoriasBootstrap

/**
 * Catálogo privado del administrador. El cliente no abre este archivo:
 * lo consulta por el ContentProvider.
 */
@Database(
    entities = [ProductoEntity::class, CategoriaEntity::class, AdministradorEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun administradorDao(): AdministradorDao

    companion object {
        private const val NOMBRE = "megamarket_catalogo.db"

        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Crea categorías, añade identidad remota a productos y FK categoriaId.
         * Conserva filas existentes. No inventa UUID.
         */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrarProductosYCategorias(db)
            }
        }

        fun migrarProductosYCategorias(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categorias` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`remote_id` TEXT, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`remote_version` INTEGER, " +
                    "`remote_updated_at` TEXT, " +
                    "`remote_deleted_at` TEXT)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categorias_nombre` ON `categorias` (`nombre`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categorias_remote_id` ON `categorias` (`remote_id`)"
            )

            CategoriasBootstrap.NOMBRES.forEach { nombre ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `categorias` " +
                        "(`nombre`, `remote_id`, `remote_version`, `remote_updated_at`, `remote_deleted_at`) " +
                        "VALUES (?, NULL, NULL, NULL, NULL)",
                    arrayOf(nombre)
                )
            }

            // categoriaId 0 o inexistente → primera categoría (Abarrotes).
            db.execSQL(
                "UPDATE productos SET categoriaId = (" +
                    "SELECT id FROM categorias ORDER BY id ASC LIMIT 1" +
                    ") WHERE categoriaId NOT IN (SELECT id FROM categorias) " +
                    "OR categoriaId IS NULL OR categoriaId <= 0"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `productos_nueva` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`remote_id` TEXT, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`marca` TEXT NOT NULL, " +
                    "`descripcion` TEXT NOT NULL, " +
                    "`categoriaId` INTEGER NOT NULL, " +
                    "`precioCentimos` INTEGER NOT NULL, " +
                    "`precioOfertaCentimos` INTEGER, " +
                    "`stock` INTEGER NOT NULL, " +
                    "`imagenKey` TEXT NOT NULL, " +
                    "`esOferta` INTEGER NOT NULL, " +
                    "`activo` INTEGER NOT NULL, " +
                    "`remote_version` INTEGER, " +
                    "`remote_updated_at` TEXT, " +
                    "`remote_deleted_at` TEXT, " +
                    "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) " +
                    "ON UPDATE CASCADE ON DELETE RESTRICT)"
            )
            db.execSQL(
                "INSERT INTO `productos_nueva` (" +
                    "`id`, `remote_id`, `nombre`, `marca`, `descripcion`, `categoriaId`, " +
                    "`precioCentimos`, `precioOfertaCentimos`, `stock`, `imagenKey`, " +
                    "`esOferta`, `activo`, `remote_version`, `remote_updated_at`, `remote_deleted_at`) " +
                    "SELECT `id`, NULL, `nombre`, `marca`, `descripcion`, `categoriaId`, " +
                    "`precioCentimos`, `precioOfertaCentimos`, `stock`, `imagenKey`, " +
                    "`esOferta`, `activo`, NULL, NULL, NULL FROM `productos`"
            )
            db.execSQL("DROP TABLE `productos`")
            db.execSQL("ALTER TABLE `productos_nueva` RENAME TO `productos`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_productos_categoriaId` ON `productos` (`categoriaId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_remote_id` ON `productos` (`remote_id`)"
            )
        }

        fun getInstance(contexto: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    AppDatabase::class.java,
                    NOMBRE
                )
                    .addMigrations(MIGRACION_2_3)
                    // La versión 1 fue un prototipo sin migración conocida.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
