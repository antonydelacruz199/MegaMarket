package com.megamarket.app.data.local

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.megamarket.modelo.ContratoCatalogo

/**
 * Publica el catálogo de Room para que el aplicativo del cliente lo lea
 * con ContentResolver. No acepta escrituras desde otras aplicaciones.
 */
class ProveedorProductos : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val contexto = checkNotNull(context) { "Proveedor sin contexto" }
        val dao = BaseDatosMegaMarket.obtener(contexto).productoDao()
        val productos = when (COMPARADOR.match(uri)) {
            LISTA -> dao.obtenerActivos()
            ITEM -> dao.obtenerPorId(ContentUris.parseId(uri))?.let { listOf(it) }.orEmpty()
            else -> throw IllegalArgumentException("URI no válida: $uri")
        }

        val cursor = MatrixCursor(ContratoCatalogo.COLUMNAS)
        productos.filter { it.activo }.forEach { producto ->
            cursor.addRow(
                arrayOf<Any?>(
                    producto.id,
                    producto.nombre,
                    producto.marca,
                    producto.descripcion,
                    producto.categoriaId,
                    producto.precioCentimos,
                    producto.precioOfertaCentimos,
                    producto.stock,
                    producto.imagenKey,
                    if (producto.esOferta) 1 else 0,
                    if (producto.activo) 1 else 0
                )
            )
        }
        cursor.setNotificationUri(contexto.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String = when (COMPARADOR.match(uri)) {
        LISTA -> "vnd.android.cursor.dir/vnd.${ContratoCatalogo.AUTORIDAD}.producto"
        ITEM -> "vnd.android.cursor.item/vnd.${ContratoCatalogo.AUTORIDAD}.producto"
        else -> throw IllegalArgumentException("URI no válida: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        const val LISTA = 1
        const val ITEM = 2

        val COMPARADOR = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(ContratoCatalogo.AUTORIDAD, ContratoCatalogo.RUTA_PRODUCTOS, LISTA)
            addURI(ContratoCatalogo.AUTORIDAD, "${ContratoCatalogo.RUTA_PRODUCTOS}/#", ITEM)
        }
    }
}
