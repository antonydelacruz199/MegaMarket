package com.megamarket.cliente.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.megamarket.cliente.data.local.ProductoProviderSnapshot
import com.megamarket.cliente.data.local.dao.CategoriaDao
import com.megamarket.cliente.data.local.dao.MovimientoInventarioDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.mapper.aEntidad
import com.megamarket.cliente.data.mapper.resolverCategoriaIdLocal
import com.megamarket.cliente.data.mapper.toEntity
import com.megamarket.cliente.data.mapper.toModel
import com.megamarket.cliente.data.remote.dto.CategoriaDto
import com.megamarket.cliente.data.remote.dto.ProductoDto
import com.megamarket.cliente.model.ResultadoDescuentoStock
import com.megamarket.modelo.Categoria
import com.megamarket.modelo.CategoriasBootstrap
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.Producto
import com.megamarket.modelo.decodificarImagen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Catálogo offline-first:
 * Room local es la fuente de la UI (Producto.id = PK cliente).
 * ContentProvider usa ProductoEntity.providerId durante la transición.
 */
class CatalogoRepository(
    private val resolver: ContentResolver,
    private val productoDao: ProductoDao,
    private val categoriaDao: CategoriaDao,
    private val movimientoDao: MovimientoInventarioDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val mutexImportacion = Mutex()
    @Volatile
    private var observacionIniciada = false

    fun observarProductos(): Flow<List<Producto>> =
        productoDao.observarActivos().map { lista -> lista.map { it.toModel() } }

    fun observarCategorias(): Flow<List<Categoria>> =
        categoriaDao.observarActivas().map { lista -> lista.map { it.toModel() } }

    fun observarProducto(id: Long): Flow<Producto?> =
        productoDao.observarPorId(id).map { it?.toModel() }

    fun observarCambiosCatalogo(): Flow<Unit> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(ContratoCatalogo.URI_PRODUCTOS, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.Main.immediate)

    fun iniciar() {
        if (observacionIniciada) return
        observacionIniciada = true
        scope.launch {
            asegurarCatalogoLocal()
            observarCambiosCatalogo().collect {
                importarDesdeProvider(silencioso = true)
            }
        }
    }

    suspend fun asegurarCatalogoLocal() = withContext(Dispatchers.IO) {
        asegurarCategoriasBootstrap()
        if (productoDao.contar() == 0) {
            importarDesdeProvider(silencioso = true)
        }
    }

    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        asegurarCategoriasBootstrap()
        var locales = productoDao.obtenerActivos().map { it.toModel() }
        if (locales.isEmpty()) {
            importarDesdeProvider(silencioso = false)
            locales = productoDao.obtenerActivos().map { it.toModel() }
        }
        locales
    }

    suspend fun obtenerPorId(id: Long): Producto? = withContext(Dispatchers.IO) {
        asegurarCatalogoLocal()
        productoDao.obtenerPorId(id)?.toModel()
    }

    suspend fun obtenerCategorias(): List<Categoria> = withContext(Dispatchers.IO) {
        asegurarCategoriasBootstrap()
        categoriaDao.obtenerActivas().map { it.toModel() }
    }

    fun leerActivos(): List<Producto> =
        runBlockingIo { productoDao.obtenerActivos().map { it.toModel() } }

    fun leerPorId(id: Long): Producto? =
        runBlockingIo { productoDao.obtenerPorId(id)?.toModel() }

    /**
     * Orden futuro cuando exista API: categorías → productos.
     * Insert con id=0 (AUTOINCREMENT). Conserva providerId en merge.
     */
    suspend fun sincronizarRemotoSiDisponible(
        categoriasRemotas: List<CategoriaDto>,
        productosRemotos: List<ProductoDto>
    ) = withContext(Dispatchers.IO) {
        for (dto in categoriasRemotas) {
            val existente = categoriaDao.obtenerPorRemoteId(dto.id)
            if (existente != null) {
                categoriaDao.actualizar(dto.aEntidad(idLocal = existente.id))
            } else {
                categoriaDao.insertar(dto.aEntidad(idLocal = 0))
            }
        }
        val categorias = categoriaDao.obtenerActivas()
        for (dto in productosRemotos) {
            val categoriaLocal = resolverCategoriaIdLocal(dto.categoriaId, categorias) ?: continue
            val existente = productoDao.obtenerPorRemoteId(dto.id)
            if (existente != null) {
                val pendienteStock = (movimientoDao?.contarPendientesDeProducto(existente.id) ?: 0) > 0
                val remoto = dto.aEntidad(
                    categoriaIdLocal = categoriaLocal,
                    idLocal = existente.id,
                    providerIdExistente = existente.providerId
                )
                // No pisar stock local si hay movimiento de inventario pendiente (RF11/conflictos).
                productoDao.actualizar(
                    if (pendienteStock) remoto.copy(stock = existente.stock) else remoto
                )
            } else {
                productoDao.insertar(
                    dto.aEntidad(
                        categoriaIdLocal = categoriaLocal,
                        idLocal = 0,
                        providerIdExistente = null
                    )
                )
            }
        }
    }

    /**
     * Importa/actualiza Room desde Provider por [ProductoProviderSnapshot.providerId].
     * No usa el id admin como PK local.
     */
    suspend fun importarDesdeProvider(silencioso: Boolean = true): Int = mutexImportacion.withLock {
        withContext(Dispatchers.IO) {
            asegurarCategoriasBootstrap()
            val snapshots = try {
                leerSnapshotsDesdeProvider(ContratoCatalogo.URI_PRODUCTOS)
            } catch (error: CatalogoNoDisponibleException) {
                if (silencioso) return@withContext 0
                throw error
            }
            if (snapshots.isEmpty()) return@withContext 0

            var importados = 0
            for (snapshot in snapshots) {
                if (persistirDesdeSnapshot(snapshot)) importados++
            }
            importados
        }
    }

    suspend fun cargarImagen(productoIdLocal: Long, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        val providerId = obtenerProviderId(productoIdLocal) ?: return@withContext null
        try {
            resolver.openInputStream(ContratoCatalogo.uriImagen(providerId))
                ?.use { entrada -> entrada.readBytes().decodificarImagen(lado) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    suspend fun descontarStock(productoIdLocal: Long, cantidad: Int): ResultadoDescuentoStock =
        withContext(Dispatchers.IO) {
            mutarStock(
                productoIdLocal = productoIdLocal,
                cantidad = cantidad,
                operacion = ContratoCatalogo.OPERACION_DESCONTAR,
                nombreParaError = { "Stock insuficiente para $it." },
                errorGenerico = "No se pudo actualizar el stock."
            )
        }

    suspend fun restaurarStock(productoIdLocal: Long, cantidad: Int): ResultadoDescuentoStock =
        withContext(Dispatchers.IO) {
            mutarStock(
                productoIdLocal = productoIdLocal,
                cantidad = cantidad,
                operacion = ContratoCatalogo.OPERACION_RESTAURAR,
                nombreParaError = { "No se pudo restaurar el stock de $it." },
                errorGenerico = "No se pudo restaurar el stock."
            )
        }

    private suspend fun obtenerProviderId(productoIdLocal: Long): Long? =
        productoDao.obtenerPorId(productoIdLocal)?.providerId

    private suspend fun asegurarCategoriasBootstrap() {
        if (categoriaDao.contar() > 0) return
        categoriaDao.insertarVarias(
            CategoriasBootstrap.NOMBRES.map { nombre -> CategoriaEntity(nombre = nombre) }
        )
    }

    /**
     * Limitación de transición: el Provider publica categoria_id de Room admin.
     * Mientras ambas apps inserten [CategoriasBootstrap] en el mismo orden, los Long coinciden.
     * Si no existe, se usa la primera categoría local.
     */
    private suspend fun resolverCategoriaLocal(categoriaProviderId: Long): Long {
        val categorias = categoriaDao.obtenerActivas()
        if (categorias.any { it.id == categoriaProviderId }) return categoriaProviderId
        return categorias.minByOrNull { it.id }?.id
            ?: error("No hay categorías locales")
    }

    private suspend fun persistirDesdeSnapshot(snapshot: ProductoProviderSnapshot): Boolean {
        val categoriaId = resolverCategoriaLocal(snapshot.categoriaProviderId)
        val existente = productoDao.obtenerPorProviderId(snapshot.providerId)
            ?: snapshot.remoteId?.takeIf { it.isNotBlank() }?.let { productoDao.obtenerPorRemoteId(it) }

        return if (existente != null) {
            productoDao.actualizar(
                snapshot.toEntity(idLocal = existente.id, categoriaIdLocal = categoriaId)
            ) > 0
        } else {
            productoDao.insertar(
                snapshot.toEntity(idLocal = 0, categoriaIdLocal = categoriaId)
            ) > 0
        }
    }

    private suspend fun mutarStock(
        productoIdLocal: Long,
        cantidad: Int,
        operacion: String,
        nombreParaError: (String) -> String,
        errorGenerico: String
    ): ResultadoDescuentoStock {
        if (cantidad <= 0) {
            return ResultadoDescuentoStock.Fallo("La cantidad no es válida")
        }
        val local = productoDao.obtenerPorId(productoIdLocal)
            ?: return ResultadoDescuentoStock.Fallo(errorGenerico)
        val providerId = local.providerId
            ?: return ResultadoDescuentoStock.Fallo(
                "El producto todavía no está disponible para actualización local de stock."
            )

        val values = ContentValues().apply {
            put(ContratoCatalogo.COL_CANTIDAD, cantidad)
            put(ContratoCatalogo.COL_OPERACION, operacion)
        }
        val filas = try {
            resolver.update(ContratoCatalogo.uriStock(providerId), values, null, null)
        } catch (error: SecurityException) {
            throw CatalogoNoDisponibleException(error)
        } catch (error: IllegalArgumentException) {
            throw CatalogoNoDisponibleException(error)
        }
        if (filas <= 0) {
            return ResultadoDescuentoStock.Fallo(nombreParaError(local.nombre))
        }

        val desdeProvider = try {
            leerSnapshotsDesdeProvider(ContratoCatalogo.uriProducto(providerId)).firstOrNull()
        } catch (_: CatalogoNoDisponibleException) {
            null
        } ?: return ResultadoDescuentoStock.Fallo(errorGenerico)

        persistirDesdeSnapshot(desdeProvider)
        return ResultadoDescuentoStock.Exito(desdeProvider.stock)
    }

    private fun leerSnapshotsDesdeProvider(uri: Uri): List<ProductoProviderSnapshot> {
        val cursor = try {
            resolver.query(uri, ContratoCatalogo.COLUMNAS, null, null, null)
                ?: resolver.query(uri, null, null, null, null)
        } catch (error: SecurityException) {
            throw CatalogoNoDisponibleException(error)
        } catch (error: IllegalArgumentException) {
            throw CatalogoNoDisponibleException(error)
        } ?: throw CatalogoNoDisponibleException()

        cursor.use { return it.aSnapshots().filter { snap -> snap.activo } }
    }

    private fun Cursor.aSnapshots(): List<ProductoProviderSnapshot> {
        val productos = mutableListOf<ProductoProviderSnapshot>()
        val id = getColumnIndexOrThrow(ContratoCatalogo.COL_ID)
        val nombre = getColumnIndexOrThrow(ContratoCatalogo.COL_NOMBRE)
        val marca = getColumnIndexOrThrow(ContratoCatalogo.COL_MARCA)
        val descripcion = getColumnIndexOrThrow(ContratoCatalogo.COL_DESCRIPCION)
        val categoria = getColumnIndexOrThrow(ContratoCatalogo.COL_CATEGORIA_ID)
        val precio = getColumnIndexOrThrow(ContratoCatalogo.COL_PRECIO_CENTIMOS)
        val oferta = getColumnIndexOrThrow(ContratoCatalogo.COL_PRECIO_OFERTA_CENTIMOS)
        val stock = getColumnIndexOrThrow(ContratoCatalogo.COL_STOCK)
        val imagen = getColumnIndexOrThrow(ContratoCatalogo.COL_IMAGEN_KEY)
        val esOferta = getColumnIndexOrThrow(ContratoCatalogo.COL_ES_OFERTA)
        val activo = getColumnIndexOrThrow(ContratoCatalogo.COL_ACTIVO)
        val remoteIdIdx = getColumnIndex(ContratoCatalogo.COL_REMOTE_ID)
        val remoteVersionIdx = getColumnIndex(ContratoCatalogo.COL_REMOTE_VERSION)
        val remoteUpdatedIdx = getColumnIndex(ContratoCatalogo.COL_REMOTE_UPDATED_AT)
        val remoteDeletedIdx = getColumnIndex(ContratoCatalogo.COL_REMOTE_DELETED_AT)

        while (moveToNext()) {
            productos += ProductoProviderSnapshot(
                providerId = getLong(id),
                remoteId = stringOpcional(remoteIdIdx),
                nombre = getString(nombre).orEmpty(),
                marca = getString(marca).orEmpty(),
                descripcion = getString(descripcion).orEmpty(),
                categoriaProviderId = getLong(categoria),
                precioCentimos = getLong(precio),
                precioOfertaCentimos = if (isNull(oferta)) null else getLong(oferta),
                stock = getInt(stock),
                imagenKey = getString(imagen).orEmpty(),
                esOferta = getInt(esOferta) != 0,
                activo = getInt(activo) != 0,
                remoteVersion = longOpcional(remoteVersionIdx),
                remoteUpdatedAt = stringOpcional(remoteUpdatedIdx),
                remoteDeletedAt = stringOpcional(remoteDeletedIdx)
            )
        }
        return productos
    }

    private fun Cursor.stringOpcional(indice: Int): String? {
        if (indice < 0 || isNull(indice)) return null
        return getString(indice)
    }

    private fun Cursor.longOpcional(indice: Int): Long? {
        if (indice < 0 || isNull(indice)) return null
        return getLong(indice)
    }

    private fun <T> runBlockingIo(bloque: suspend () -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { bloque() }

    companion object {
        const val MENSAJE_ERROR =
            "No se pudo leer el catálogo. Instala MegaMarket Express en este teléfono."
        const val MENSAJE_SIN_PROVIDER_ID =
            "El producto todavía no está disponible para actualización local de stock."
    }
}

class CatalogoNoDisponibleException(causa: Throwable? = null) :
    IllegalStateException(CatalogoRepository.MENSAJE_ERROR, causa)
