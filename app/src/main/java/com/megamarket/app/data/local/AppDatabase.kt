package com.megamarket.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.dao.MovimientoInventarioDao
import com.megamarket.app.data.local.dao.OperacionPendienteDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.dao.SyncMetadataDao
import com.megamarket.app.data.local.entities.AdministradorEntity
import com.megamarket.app.data.local.entities.CategoriaEntity
import com.megamarket.app.data.local.entities.MovimientoInventarioEntity
import com.megamarket.app.data.local.entities.OperacionPendienteEntity
import com.megamarket.app.data.local.entities.ProductoEntity
import com.megamarket.app.data.local.entities.SyncMetadataEntity
import com.megamarket.modelo.CategoriasBootstrap
import com.megamarket.modelo.PrecioDescuento

/**
 * Catálogo privado del administrador. El cliente no abre este archivo:
 * lo consulta por el ContentProvider.
 */
@Database(
    entities = [
        ProductoEntity::class,
        CategoriaEntity::class,
        AdministradorEntity::class,
        OperacionPendienteEntity::class,
        MovimientoInventarioEntity::class,
        SyncMetadataEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun administradorDao(): AdministradorDao
    abstract fun operacionPendienteDao(): OperacionPendienteDao
    abstract fun movimientoInventarioDao(): MovimientoInventarioDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        private const val NOMBRE = "megamarket_catalogo.db"

        @Volatile
        private var instancia: AppDatabase? = null

        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrarProductosYCategorias(db)
            }
        }

        /** Offline-first: cola con UUID, movimientos, metadata, descuento %. */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrarAVersion4(db)
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

        fun migrarAVersion4(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `productos` ADD COLUMN `descuento_porcentaje` INTEGER NOT NULL DEFAULT 0"
            )
            db.query(
                "SELECT `id`, `precioCentimos`, `precioOfertaCentimos`, `esOferta` FROM `productos`"
            ).use { cursor ->
                val iId = cursor.getColumnIndex("id")
                val iPrecio = cursor.getColumnIndex("precioCentimos")
                val iOferta = cursor.getColumnIndex("precioOfertaCentimos")
                val iEsOferta = cursor.getColumnIndex("esOferta")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(iId)
                    val precio = cursor.getLong(iPrecio)
                    val oferta = if (cursor.isNull(iOferta)) null else cursor.getLong(iOferta)
                    val esOferta = cursor.getInt(iEsOferta) != 0
                    val descuento = if (esOferta) {
                        PrecioDescuento.descuentoDesdePrecioOferta(precio, oferta)
                    } else {
                        0
                    }
                    db.execSQL(
                        "UPDATE `productos` SET `descuento_porcentaje` = ? WHERE `id` = ?",
                        arrayOf(descuento, id)
                    )
                }
            }

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `operaciones_pendientes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uuidOperacion` TEXT NOT NULL, " +
                    "`tipoEntidad` TEXT NOT NULL, " +
                    "`entidadIdLocal` INTEGER NOT NULL, " +
                    "`operacion` TEXT NOT NULL, " +
                    "`payload` TEXT NOT NULL, " +
                    "`estado` TEXT NOT NULL, " +
                    "`intentos` INTEGER NOT NULL, " +
                    "`ultimoError` TEXT, " +
                    "`fechaCreacion` INTEGER NOT NULL, " +
                    "`fechaActualizacion` INTEGER NOT NULL, " +
                    "`sincronizadoEn` INTEGER)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_operaciones_pendientes_uuidOperacion` " +
                    "ON `operaciones_pendientes` (`uuidOperacion`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_operaciones_pendientes_estado` " +
                    "ON `operaciones_pendientes` (`estado`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_operaciones_pendientes_tipoEntidad_entidadIdLocal_operacion` " +
                    "ON `operaciones_pendientes` (`tipoEntidad`, `entidadIdLocal`, `operacion`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `movimientos_inventario` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uuidOperacion` TEXT NOT NULL, " +
                    "`remote_id` TEXT, " +
                    "`productoId` INTEGER NOT NULL, " +
                    "`pedidoId` INTEGER, " +
                    "`tipo` TEXT NOT NULL, " +
                    "`cantidad` INTEGER NOT NULL, " +
                    "`fechaCreacion` INTEGER NOT NULL, " +
                    "`remote_version` INTEGER, " +
                    "FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) " +
                    "ON UPDATE CASCADE ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_movimientos_inventario_uuidOperacion` " +
                    "ON `movimientos_inventario` (`uuidOperacion`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_movimientos_inventario_productoId` " +
                    "ON `movimientos_inventario` (`productoId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_metadata` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`ultimaSincronizacionExitosa` INTEGER, " +
                    "`ultimoIntento` INTEGER, " +
                    "`ultimoErrorGeneral` TEXT, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `sync_metadata` " +
                    "(`id`, `ultimaSincronizacionExitosa`, `ultimoIntento`, `ultimoErrorGeneral`) " +
                    "VALUES (1, NULL, NULL, NULL)"
            )
        }

        fun getInstance(contexto: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    AppDatabase::class.java,
                    NOMBRE
                )
                    .addMigrations(MIGRACION_2_3, MIGRACION_3_4)
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
