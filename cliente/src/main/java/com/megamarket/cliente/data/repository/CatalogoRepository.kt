package com.megamarket.cliente.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.megamarket.cliente.data.local.dao.CategoriaDao
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
 * Room local es la fuente de la UI.
 * ContentProvider del admin es bootstrap / sincronización de stock durante la transición.
 */
class CatalogoRepository(
    private val resolver: ContentResolver,
    private val productoDao: ProductoDao,
    private val categoriaDao: CategoriaDao,
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

    /** Emite cambios del Provider para que el repositorio actualice Room (no la UI). */
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

    /**
     * Arranca bootstrap + ContentObserver → Room.
     * Idempotente; llamar desde [AppContainer] / Application.
     */
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

    /** Lectura bloqueante desde Room (checkout / carrito en hilo IO). */
    fun leerActivos(): List<Producto> =
        runBlockingIo { productoDao.obtenerActivos().map { it.toModel() } }

    fun leerPorId(id: Long): Producto? =
        runBlockingIo { productoDao.obtenerPorId(id)?.toModel() }

    /**
     * Importa/actualiza Room desde el Provider del administrador.
     * Segunda llamada hace upsert por id local; no duplica filas.
     */
    /**
     * Orden futuro cuando exista API: categorías → productos.
     * No se invoca automáticamente; evita romper la app sin backend.
     */
    suspend fun sincronizarRemotoSiDisponible(
        categoriasRemotas: List<CategoriaDto>,
        productosRemotos: List<ProductoDto>
    ) = withContext(Dispatchers.IO) {
        for (dto in categoriasRemotas) {
            val existente = categoriaDao.obtenerPorRemoteId(dto.id)
            categoriaDao.upsert(dto.aEntidad(idLocal = existente?.id))
        }
        val categorias = categoriaDao.obtenerActivas()
        for (dto in productosRemotos) {
            val categoriaLocal = resolverCategoriaIdLocal(dto.categoriaId, categorias) ?: continue
            val existente = productoDao.obtenerPorRemoteId(dto.id)
            val idLocal = existente?.id ?: (productoDao.maxId() + 1)
            productoDao.upsert(dto.aEntidad(categoriaIdLocal = categoriaLocal, idLocal = idLocal))
        }
    }

    suspend fun importarDesdeProvider(silencioso: Boolean = true): Int = mutexImportacion.withLock {
        withContext(Dispatchers.IO) {
            asegurarCategoriasBootstrap()
            val remotos = try {
                leerDesdeProvider(ContratoCatalogo.URI_PRODUCTOS)
            } catch (error: CatalogoNoDisponibleException) {
                if (silencioso) return@withContext 0
                throw error
            }
            if (remotos.isEmpty()) return@withContext 0

            val categorias = categoriaDao.obtenerActivas()
            val primeraCategoria = categorias.minByOrNull { it.id }?.id
                ?: return@withContext 0
            val idsCategoria = categorias.map { it.id }.toSet()

            val entidades = remotos.map { producto ->
                val categoriaId = if (producto.categoriaId in idsCategoria) {
                    producto.categoriaId
                } else {
                    primeraCategoria
                }
                producto.copy(categoriaId = categoriaId).toEntity()
            }
            productoDao.upsertVarios(entidades)
            entidades.size
        }
    }

    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(ContratoCatalogo.uriImagen(productoId))
                ?.use { entrada -> entrada.readBytes().decodificarImagen(lado) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Descuenta stock en admin vía Provider y refleja el stock final en Room cliente.
     */
    suspend fun descontarStock(productoId: Long, cantidad: Int): ResultadoDescuentoStock =
        withContext(Dispatchers.IO) {
            mutarStock(
                productoId = productoId,
                cantidad = cantidad,
                operacion = ContratoCatalogo.OPERACION_DESCONTAR,
                nombreParaError = { "Stock insuficiente para $it." },
                errorGenerico = "No se pudo actualizar el stock."
            )
        }

    suspend fun restaurarStock(productoId: Long, cantidad: Int): ResultadoDescuentoStock =
        withContext(Dispatchers.IO) {
            mutarStock(
                productoId = productoId,
                cantidad = cantidad,
                operacion = ContratoCatalogo.OPERACION_RESTAURAR,
                nombreParaError = { "No se pudo restaurar el stock de $it." },
                errorGenerico = "No se pudo restaurar el stock."
            )
        }

    private suspend fun asegurarCategoriasBootstrap() {
        if (categoriaDao.contar() > 0) return
        categoriaDao.insertarVarias(
            CategoriasBootstrap.NOMBRES.map { nombre -> CategoriaEntity(nombre = nombre) }
        )
    }

    private suspend fun mutarStock(
        productoId: Long,
        cantidad: Int,
        operacion: String,
        nombreParaError: (String) -> String,
        errorGenerico: String
    ): ResultadoDescuentoStock {
        if (cantidad <= 0) {
            return ResultadoDescuentoStock.Fallo("La cantidad no es válida")
        }
        val values = ContentValues().apply {
            put(ContratoCatalogo.COL_CANTIDAD, cantidad)
            put(ContratoCatalogo.COL_OPERACION, operacion)
        }
        val filas = try {
            resolver.update(ContratoCatalogo.uriStock(productoId), values, null, null)
        } catch (error: SecurityException) {
            throw CatalogoNoDisponibleException(error)
        } catch (error: IllegalArgumentException) {
            throw CatalogoNoDisponibleException(error)
        }
        if (filas <= 0) {
            val nombre = productoDao.obtenerPorId(productoId)?.nombre
                ?: leerDesdeProvider(ContratoCatalogo.uriProducto(productoId)).firstOrNull()?.nombre
            return ResultadoDescuentoStock.Fallo(
                if (nombre != null) nombreParaError(nombre) else errorGenerico
            )
        }

        val desdeProvider = try {
            leerDesdeProvider(ContratoCatalogo.uriProducto(productoId)).firstOrNull()
        } catch (_: CatalogoNoDisponibleException) {
            null
        } ?: return ResultadoDescuentoStock.Fallo(errorGenerico)

        sincronizarProductoLocal(desdeProvider)
        return ResultadoDescuentoStock.Exito(desdeProvider.stock)
    }

    private suspend fun sincronizarProductoLocal(producto: Producto) {
        asegurarCategoriasBootstrap()
        val categorias = categoriaDao.obtenerActivas()
        val ids = categorias.map { it.id }.toSet()
        val primera = categorias.minByOrNull { it.id }?.id ?: return
        val categoriaId = if (producto.categoriaId in ids) producto.categoriaId else primera
        productoDao.upsert(producto.copy(categoriaId = categoriaId).toEntity())
    }

    private fun leerDesdeProvider(uri: Uri): List<Producto> {
        val cursor = try {
            resolver.query(uri, ContratoCatalogo.COLUMNAS, null, null, null)
                ?: resolver.query(uri, null, null, null, null)
        } catch (error: SecurityException) {
            throw CatalogoNoDisponibleException(error)
        } catch (error: IllegalArgumentException) {
            throw CatalogoNoDisponibleException(error)
        } ?: throw CatalogoNoDisponibleException()

        cursor.use { return it.aProductos().filter { producto -> producto.activo } }
    }

    private fun Cursor.aProductos(): List<Producto> {
        val productos = mutableListOf<Producto>()
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
            productos += Producto(
                id = getLong(id),
                nombre = getString(nombre).orEmpty(),
                marca = getString(marca).orEmpty(),
                descripcion = getString(descripcion).orEmpty(),
                categoriaId = getLong(categoria),
                precioCentimos = getLong(precio),
                precioOfertaCentimos = if (isNull(oferta)) null else getLong(oferta),
                stock = getInt(stock),
                imagenKey = getString(imagen).orEmpty(),
                esOferta = getInt(esOferta) != 0,
                activo = getInt(activo) != 0,
                remoteId = stringOpcional(remoteIdIdx),
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

    /** Evita exponer runBlocking en la API pública; solo helpers de lectura síncrona en IO. */
    private fun <T> runBlockingIo(bloque: suspend () -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { bloque() }

    companion object {
        const val MENSAJE_ERROR =
            "No se pudo leer el catálogo. Instala MegaMarket Express en este teléfono."
    }
}

/** El ContentProvider del administrador no respondió (app no instalada o sin permiso). */
class CatalogoNoDisponibleException(causa: Throwable? = null) :
    IllegalStateException(CatalogoRepository.MENSAJE_ERROR, causa)
