package com.megamarket.cliente.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.megamarket.cliente.data.local.dao.CarritoDao
import com.megamarket.cliente.data.local.dao.ClienteDao
import com.megamarket.cliente.data.local.dao.FavoritoDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.entities.CarritoEntity
import com.megamarket.cliente.data.local.entities.ClienteEntity
import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.FavoritoEntity
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity

@Database(
    entities = [
        CarritoEntity::class,
        FavoritoEntity::class,
        ClienteEntity::class,
        PedidoEntity::class,
        PedidoDetalleEntity::class,
        DireccionEntity::class,
        OperacionPendienteEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carritoDao(): CarritoDao
    abstract fun favoritoDao(): FavoritoDao
    abstract fun clienteDao(): ClienteDao
    abstract fun pedidoDao(): PedidoDao
    abstract fun operacionPendienteDao(): OperacionPendienteDao

    companion object {
        private const val NOMBRE = "megamarket_cliente.db"

        @Volatile
        private var instancia: AppDatabase? = null

        /** Agrega pedidos, su detalle y su dirección sin tocar clientes, carrito ni favoritos. */
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

        /**
         * Crea la cola de sincronización sin destruir datos existentes.
         * Conserva clientes, carrito, favoritos, pedidos, pedido_detalle y direcciones.
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                crearTablaOperacionesPendientes(db)
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

        fun getInstance(contexto: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    AppDatabase::class.java,
                    NOMBRE
                )
                    .addMigrations(MIGRACION_2_3, MIGRACION_3_4)
                    // La versión 1 fue un prototipo sin migración conocida.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .build()
                    .also { instancia = it }
            }
        }
    }
}
