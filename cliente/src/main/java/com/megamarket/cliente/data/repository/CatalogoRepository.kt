package com.megamarket.cliente.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.Producto
import com.megamarket.modelo.decodificarImagen
import kotlinx.coroutines.Dispatchers
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

    /** Devuelve null si el producto no tiene imagen o el administrador no la publica. */
    suspend fun cargarImagen(productoId: Long, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(ContratoCatalogo.uriImagen(productoId))
                ?.use { entrada -> entrada.readBytes().decodificarImagen(lado) }
        } catch (error: IOException) {
            null
        } catch (error: SecurityException) {
            null
        }
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
