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
import com.megamarket.cliente.data.local.dao.MovimientoInventarioDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.local.entities.CarritoEntity
import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.local.entities.ClienteEntity
import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.FavoritoEntity
import com.megamarket.cliente.data.local.entities.MovimientoInventarioEntity
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.local.entities.SyncMetadataEntity
import com.megamarket.modelo.CategoriasBootstrap
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.PrecioDescuento
import java.util.UUID

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
        ProductoEntity::class,
        MovimientoInventarioEntity::class,
        SyncMetadataEntity::class
    ],
    version = 7,
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
    abstract fun movimientoInventarioDao(): MovimientoInventarioDao
    abstract fun syncMetadataDao(): SyncMetadataDao

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
                crearTablaOperacionesPendientesLegacy(db)
            }
        }

        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                crearCatalogoLocal(db)
            }
        }

        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrarProductosProviderId(db)
            }
        }

        /**
         * Offline-first académico: movimientos, ops con UUID, sync pedidos, descuento %, metadata.
         */
        val MIGRACION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrarAVersion7(db)
            }
        }

        fun crearTablaOperacionesPendientesLegacy(db: SupportSQLiteDatabase) {
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

        fun migrarAVersion7(db: SupportSQLiteDatabase) {
            val ahora = System.currentTimeMillis()

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
                "CREATE TABLE IF NOT EXISTS `pedidos_nueva` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`clienteId` INTEGER NOT NULL, " +
                    "`clientUuid` TEXT NOT NULL, " +
                    "`remote_id` TEXT, " +
                    "`remote_version` INTEGER, " +
                    "`fecha` INTEGER NOT NULL, " +
                    "`totalCentimos` INTEGER NOT NULL, " +
                    "`estado` TEXT NOT NULL, " +
                    "`estadoSync` TEXT NOT NULL, " +
                    "FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.query("SELECT `id`, `clienteId`, `fecha`, `totalCentimos`, `estado` FROM `pedidos`")
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "INSERT INTO `pedidos_nueva` (" +
                                "`id`, `clienteId`, `clientUuid`, `remote_id`, `remote_version`, " +
                                "`fecha`, `totalCentimos`, `estado`, `estadoSync`) " +
                                "VALUES (?, ?, ?, NULL, NULL, ?, ?, ?, ?)",
                            arrayOf(
                                cursor.getLong(0),
                                cursor.getLong(1),
                                UUID.randomUUID().toString(),
                                cursor.getLong(2),
                                cursor.getLong(3),
                                cursor.getString(4),
                                EstadoSincronizacion.SINCRONIZADO
                            )
                        )
                    }
                }
            db.execSQL("DROP TABLE `pedidos`")
            db.execSQL("ALTER TABLE `pedidos_nueva` RENAME TO `pedidos`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pedidos_clienteId` ON `pedidos` (`clienteId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pedidos_clientUuid` ON `pedidos` (`clientUuid`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pedidos_remote_id` ON `pedidos` (`remote_id`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `operaciones_pendientes_nueva` (" +
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
            db.query("SELECT * FROM `operaciones_pendientes`").use { cursor ->
                val iTipo = cursor.getColumnIndex("tipoEntidad")
                val iEntidad = cursor.getColumnIndex("entidadIdLocal")
                val iOp = cursor.getColumnIndex("operacion")
                val iPayload = cursor.getColumnIndex("payload")
                val iFecha = cursor.getColumnIndex("fechaCreacion")
                val iIntentos = cursor.getColumnIndex("intentos")
                val iError = cursor.getColumnIndex("ultimoError")
                val iEstado = cursor.getColumnIndex("estado")
                while (cursor.moveToNext()) {
                    val operacion = cursor.getString(iOp)
                    val estadoOld = cursor.getString(iEstado)
                    val esLegacyStock = operacion == "ACTUALIZAR_STOCK"
                    val estado = when {
                        esLegacyStock -> EstadoSincronizacion.ERROR
                        estadoOld == "SINCRONIZANDO" || estadoOld == "ENVIANDO" ->
                            EstadoSincronizacion.PENDIENTE
                        estadoOld == "ERROR" -> EstadoSincronizacion.ERROR
                        else -> EstadoSincronizacion.PENDIENTE
                    }
                    val error = if (esLegacyStock) {
                        "Operación legacy ACTUALIZAR_STOCK incompatible con movimientos de inventario."
                    } else {
                        if (cursor.isNull(iError)) null else cursor.getString(iError)
                    }
                    db.execSQL(
                        "INSERT INTO `operaciones_pendientes_nueva` (" +
                            "`uuidOperacion`, `tipoEntidad`, `entidadIdLocal`, `operacion`, " +
                            "`payload`, `estado`, `intentos`, `ultimoError`, `fechaCreacion`, " +
                            "`fechaActualizacion`, `sincronizadoEn`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)",
                        arrayOf(
                            UUID.randomUUID().toString(),
                            cursor.getString(iTipo),
                            cursor.getLong(iEntidad),
                            operacion,
                            cursor.getString(iPayload),
                            estado,
                            cursor.getInt(iIntentos),
                            error,
                            cursor.getLong(iFecha),
                            ahora
                        )
                    )
                }
            }
            db.execSQL("DROP TABLE `operaciones_pendientes`")
            db.execSQL("ALTER TABLE `operaciones_pendientes_nueva` RENAME TO `operaciones_pendientes`")
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
                "CREATE INDEX IF NOT EXISTS `index_movimientos_inventario_pedidoId` " +
                    "ON `movimientos_inventario` (`pedidoId`)"
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
                    .addMigrations(
                        MIGRACION_2_3,
                        MIGRACION_3_4,
                        MIGRACION_4_5,
                        MIGRACION_5_6,
                        MIGRACION_6_7
                    )
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
