package com.megamarket.cliente.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.megamarket.cliente.model.ResultadoDescuentoStock
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.Producto
import com.megamarket.modelo.decodificarImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/** Única puerta del cliente hacia el ContentProvider del administrador. */
class CatalogoRepository(
    private val resolver: ContentResolver
) {
    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        leerActivos()
    }

    suspend fun obtenerPorId(id: Long): Producto? = withContext(Dispatchers.IO) {
        leerPorId(id)
    }

    /** Lectura bloqueante; usar solo desde un hilo de IO o dentro de una transacción. */
    fun leerActivos(): List<Producto> = leer(ContratoCatalogo.URI_PRODUCTOS)

    fun leerPorId(id: Long): Producto? = leer(ContratoCatalogo.uriProducto(id)).firstOrNull()

    /**
     * Emite cuando el administrador notifica cambios del catálogo (p. ej. stock).
     * No se registra en Composables: lo consumen repositorios / ViewModels.
     */
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

    /** Devuelve null si el producto no tiene imagen o el administrador no la publica. */
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
     * Pide al administrador descontar stock. Solo vía URI de operación controlada.
     * @return stock final si tuvo éxito.
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

    /** Compensación interna: restaura unidades ya descontadas. */
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

    private fun mutarStock(
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
            val nombre = leerPorId(productoId)?.nombre
            return ResultadoDescuentoStock.Fallo(
                if (nombre != null) nombreParaError(nombre) else errorGenerico
            )
        }
        val stockFinal = leerPorId(productoId)?.stock
            ?: return ResultadoDescuentoStock.Fallo(errorGenerico)
        return ResultadoDescuentoStock.Exito(stockFinal)
    }

    private fun leer(uri: Uri): List<Producto> {
        val cursor = try {
            resolver.query(uri, ContratoCatalogo.COLUMNAS, null, null, null)
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
                activo = getInt(activo) != 0
            )
        }
        return productos
    }

    companion object {
        const val MENSAJE_ERROR =
            "No se pudo leer el catálogo. Instala MegaMarket Express en este teléfono."
    }
}

/** El ContentProvider del administrador no respondió (app no instalada o sin permiso). */
class CatalogoNoDisponibleException(causa: Throwable? = null) :
    IllegalStateException(CatalogoRepository.MENSAJE_ERROR, causa)
