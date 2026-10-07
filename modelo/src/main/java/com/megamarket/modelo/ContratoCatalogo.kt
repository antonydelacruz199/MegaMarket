package com.megamarket.modelo

import android.content.ContentUris
import android.net.Uri

/**
 * Contrato del catálogo publicado por el administrador.
 * El cliente lo usa como bootstrap temporal hacia su Room local.
 *
 * Escritura restringida: solo descuento/restauración de stock.
 */
object ContratoCatalogo {
    const val AUTORIDAD = "com.megamarket.app.proveedor.productos"
    const val PERMISO_LECTURA = "com.megamarket.app.permission.LEER_PRODUCTOS"
    const val PERMISO_ACTUALIZAR_STOCK = "com.megamarket.app.permission.ACTUALIZAR_STOCK"
    const val RUTA_PRODUCTOS = "productos"
    const val RUTA_STOCK = "stock"

    val URI_PRODUCTOS: Uri = Uri.parse("content://$AUTORIDAD/$RUTA_PRODUCTOS")

    const val COL_ID = "id"
    const val COL_REMOTE_ID = "remote_id"
    const val COL_NOMBRE = "nombre"
    const val COL_MARCA = "marca"
    const val COL_DESCRIPCION = "descripcion"
    const val COL_CATEGORIA_ID = "categoria_id"
    const val COL_PRECIO_CENTIMOS = "precio_centimos"
    const val COL_PRECIO_OFERTA_CENTIMOS = "precio_oferta_centimos"
    const val COL_STOCK = "stock"
    const val COL_IMAGEN_KEY = "imagen_key"
    const val COL_ES_OFERTA = "es_oferta"
    const val COL_ACTIVO = "activo"
    const val COL_REMOTE_VERSION = "remote_version"
    const val COL_REMOTE_UPDATED_AT = "remote_updated_at"
    const val COL_REMOTE_DELETED_AT = "remote_deleted_at"

    const val COL_CANTIDAD = "cantidad"
    const val COL_OPERACION = "operacion"
    const val OPERACION_DESCONTAR = "descontar"
    const val OPERACION_RESTAURAR = "restaurar"

    val COLUMNAS: Array<String> = arrayOf(
        COL_ID,
        COL_REMOTE_ID,
        COL_NOMBRE,
        COL_MARCA,
        COL_DESCRIPCION,
        COL_CATEGORIA_ID,
        COL_PRECIO_CENTIMOS,
        COL_PRECIO_OFERTA_CENTIMOS,
        COL_STOCK,
        COL_IMAGEN_KEY,
        COL_ES_OFERTA,
        COL_ACTIVO,
        COL_REMOTE_VERSION,
        COL_REMOTE_UPDATED_AT,
        COL_REMOTE_DELETED_AT
    )

    fun uriProducto(id: Long): Uri = ContentUris.withAppendedId(URI_PRODUCTOS, id)

    fun uriImagen(id: Long): Uri = Uri.withAppendedPath(uriProducto(id), "imagen")

    fun uriStock(id: Long): Uri = Uri.withAppendedPath(uriProducto(id), RUTA_STOCK)
}
