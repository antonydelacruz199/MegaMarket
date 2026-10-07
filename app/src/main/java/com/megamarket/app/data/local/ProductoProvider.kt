package com.megamarket.app.data.local

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.megamarket.modelo.ContratoCatalogo
import java.io.FileNotFoundException

/**
 * Publica el catálogo de Room para el cliente.
 * Lectura general + única escritura controlada: descontar/restaurar stock.
 */
class ProductoProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val contexto = checkNotNull(context) { "Proveedor sin contexto" }
        val dao = AppDatabase.getInstance(contexto).productoDao()
        val productos = when (COMPARADOR.match(uri)) {
            LISTA -> dao.obtenerActivos()
            ITEM -> dao.obtenerPorId(ContentUris.parseId(uri))?.let { listOf(it) }.orEmpty()
            else -> throw IllegalArgumentException("URI no válida: $uri")
        }

        val cursor = MatrixCursor(ContratoCatalogo.COLUMNAS)
        productos
            .filter { it.activo && it.remoteDeletedAt.isNullOrBlank() }
            .forEach { producto ->
                cursor.addRow(
                    arrayOf<Any?>(
                        producto.id,
                        producto.remoteId,
                        producto.nombre,
                        producto.marca,
                        producto.descripcion,
                        producto.categoriaId,
                        producto.precioCentimos,
                        producto.precioOfertaCentimos,
                        producto.stock,
                        producto.imagenKey,
                        if (producto.esOferta) 1 else 0,
                        if (producto.activo) 1 else 0,
                        producto.remoteVersion,
                        producto.remoteUpdatedAt,
                        producto.remoteDeletedAt
                    )
                )
            }
        cursor.setNotificationUri(contexto.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String = when (COMPARADOR.match(uri)) {
        LISTA -> "vnd.android.cursor.dir/vnd.${ContratoCatalogo.AUTORIDAD}.producto"
        ITEM -> "vnd.android.cursor.item/vnd.${ContratoCatalogo.AUTORIDAD}.producto"
        IMAGEN -> "image/jpeg"
        STOCK -> "vnd.android.cursor.item/vnd.${ContratoCatalogo.AUTORIDAD}.stock"
        else -> throw IllegalArgumentException("URI no válida: $uri")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (COMPARADOR.match(uri) != IMAGEN || mode.contains("w")) {
            throw FileNotFoundException("Imagen no disponible")
        }
        val contexto = checkNotNull(context)
        val id = uri.pathSegments.getOrNull(1)?.toLongOrNull()
            ?: throw FileNotFoundException("Imagen no disponible")
        val producto = AppDatabase.getInstance(contexto).productoDao().obtenerPorId(id)
            ?.takeIf { it.activo && it.remoteDeletedAt.isNullOrBlank() }
            ?: throw FileNotFoundException("Imagen no disponible")
        val archivo = AlmacenImagenes.archivo(contexto, producto.imagenKey)
            ?: throw FileNotFoundException("Imagen no disponible")
        return ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        if (COMPARADOR.match(uri) != STOCK) return 0
        if (values == null) return 0

        val productoId = uri.pathSegments.getOrNull(1)?.toLongOrNull() ?: return 0
        val cantidad = values.getAsInteger(ContratoCatalogo.COL_CANTIDAD) ?: return 0
        if (cantidad <= 0) return 0

        val operacion = values.getAsString(ContratoCatalogo.COL_OPERACION)
            ?: ContratoCatalogo.OPERACION_DESCONTAR
        val contexto = checkNotNull(context)
        val dao = AppDatabase.getInstance(contexto).productoDao()

        val filas = when (operacion) {
            ContratoCatalogo.OPERACION_DESCONTAR -> dao.descontarStock(productoId, cantidad)
            ContratoCatalogo.OPERACION_RESTAURAR -> dao.incrementarStock(productoId, cantidad)
            else -> 0
        }
        if (filas > 0) {
            contexto.contentResolver.notifyChange(ContratoCatalogo.URI_PRODUCTOS, null)
            contexto.contentResolver.notifyChange(ContratoCatalogo.uriProducto(productoId), null)
            contexto.contentResolver.notifyChange(ContratoCatalogo.uriStock(productoId), null)
        }
        return filas
    }

    private companion object {
        const val LISTA = 1
        const val ITEM = 2
        const val IMAGEN = 3
        const val STOCK = 4

        val COMPARADOR = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(ContratoCatalogo.AUTORIDAD, ContratoCatalogo.RUTA_PRODUCTOS, LISTA)
            addURI(ContratoCatalogo.AUTORIDAD, "${ContratoCatalogo.RUTA_PRODUCTOS}/#", ITEM)
            addURI(ContratoCatalogo.AUTORIDAD, "${ContratoCatalogo.RUTA_PRODUCTOS}/#/imagen", IMAGEN)
            addURI(
                ContratoCatalogo.AUTORIDAD,
                "${ContratoCatalogo.RUTA_PRODUCTOS}/#/${ContratoCatalogo.RUTA_STOCK}",
                STOCK
            )
        }
    }
}
