package com.megamarket.cliente.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.dao.CategoriaDao
import com.megamarket.cliente.data.local.dao.ClienteDao
import com.megamarket.cliente.data.local.dao.FavoritoDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.entities.CarritoEntity
import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.local.entities.ClienteEntity
import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.FavoritoEntity
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.modelo.CategoriasBootstrap

@Database(
    entities = [
        CarritoEntity::class,
        FavoritoEntity::class,
        ClienteEntity::class,
        PedidoEntity::class,
        PedidoDetalleEntity::class,
        DireccionEntity::class,
        OperacionPendienteEntity::class,
        CategoriaEntity::class,
        ProductoEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carritoDao(): CarritoDao
    abstract fun favoritoDao(): FavoritoDao
    abstract fun clienteDao(): ClienteDao
    abstract fun pedidoDao(): PedidoDao
    abstract fun operacionPendienteDao(): OperacionPendienteDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun productoDao(): ProductoDao

    companion object {
        private const val NOMBRE = "megamarket_cliente.db"

        @Volatile
        private var instancia: AppDatabase? = null

        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pedidos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`clienteId` INTEGER NOT NULL, " +
                        "`fecha` INTEGER NOT NULL, " +
                        "`totalCentimos` INTEGER NOT NULL, " +
                        "`estado` TEXT NOT NULL, " +
                        "FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pedidos_clienteId` ON `pedidos` (`clienteId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pedido_detalle` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pedidoId` INTEGER NOT NULL, " +
                        "`productoId` INTEGER NOT NULL, " +
                        "`nombreProducto` TEXT NOT NULL, " +
                        "`precioUnitarioCentimos` INTEGER NOT NULL, " +
                        "`cantidad` INTEGER NOT NULL, " +
                        "`subtotalCentimos` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`pedidoId`) REFERENCES `pedidos`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pedido_detalle_pedidoId` " +
                        "ON `pedido_detalle` (`pedidoId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `direcciones` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pedidoId` INTEGER NOT NULL, " +
                        "`departamento` TEXT NOT NULL, " +
                        "`provincia` TEXT NOT NULL, " +
                        "`distrito` TEXT NOT NULL, " +
                        "`direccion` TEXT NOT NULL, " +
                        "`telefono` TEXT NOT NULL, " +
                        "FOREIGN KEY(`pedidoId`) REFERENCES `pedidos`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_direcciones_pedidoId` " +
                        "ON `direcciones` (`pedidoId`)"
                )
            }
        }

        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                crearTablaOperacionesPendientes(db)
            }
        }

        /** Catálogo local offline-first. Conserva carrito, pedidos y cola de sync. */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                crearCatalogoLocal(db)
            }
        }

        /**
         * Desacopla PK local del ID del Provider.
         * Conserva id existente y copia id → provider_id para no romper carrito/favoritos/pedidos/ops.
         */
        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrarProductosProviderId(db)
            }
        }

        fun crearTablaOperacionesPendientes(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `operaciones_pendientes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tipoEntidad` TEXT NOT NULL, " +
                    "`entidadIdLocal` INTEGER NOT NULL, " +
                    "`operacion` TEXT NOT NULL, " +
                    "`payload` TEXT NOT NULL, " +
                    "`fechaCreacion` INTEGER NOT NULL, " +
                    "`intentos` INTEGER NOT NULL, " +
                    "`ultimoError` TEXT, " +
                    "`estado` TEXT NOT NULL)"
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
        }

        fun crearCatalogoLocal(db: SupportSQLiteDatabase) {
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
                "CREATE TABLE IF NOT EXISTS `productos` (" +
                    "`id` INTEGER NOT NULL, " +
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
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) " +
                    "ON UPDATE CASCADE ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_productos_categoriaId` ON `productos` (`categoriaId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_remote_id` ON `productos` (`remote_id`)"
            )
        }

        fun migrarProductosProviderId(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `productos_nueva` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`provider_id` INTEGER, " +
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
                    "`id`, `provider_id`, `remote_id`, `nombre`, `marca`, `descripcion`, " +
                    "`categoriaId`, `precioCentimos`, `precioOfertaCentimos`, `stock`, " +
                    "`imagenKey`, `esOferta`, `activo`, `remote_version`, `remote_updated_at`, " +
                    "`remote_deleted_at`) " +
                    "SELECT `id`, `id`, `remote_id`, `nombre`, `marca`, `descripcion`, " +
                    "`categoriaId`, `precioCentimos`, `precioOfertaCentimos`, `stock`, " +
                    "`imagenKey`, `esOferta`, `activo`, `remote_version`, `remote_updated_at`, " +
                    "`remote_deleted_at` FROM `productos`"
            )
            db.execSQL("DROP TABLE `productos`")
            db.execSQL("ALTER TABLE `productos_nueva` RENAME TO `productos`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_productos_categoriaId` ON `productos` (`categoriaId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_productos_provider_id` " +
                    "ON `productos` (`provider_id`)"
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
                    .addMigrations(MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6)
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
