package com.megamarket.modelo

import android.content.ContentUris
import android.net.Uri

/**
 * Contrato del catálogo publicado por el administrador.
 * El cliente lee estos mismos nombres con ContentResolver.
 */
object ContratoCatalogo {
    const val AUTORIDAD = "com.megamarket.app.proveedor.productos"
    const val PERMISO_LECTURA = "com.megamarket.app.permission.LEER_PRODUCTOS"
    const val RUTA_PRODUCTOS = "productos"

    val URI_PRODUCTOS: Uri = Uri.parse("content://$AUTORIDAD/$RUTA_PRODUCTOS")

    const val COL_ID = "id"
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

    val COLUMNAS: Array<String> = arrayOf(
        COL_ID,
        COL_NOMBRE,
        COL_MARCA,
        COL_DESCRIPCION,
        COL_CATEGORIA_ID,
        COL_PRECIO_CENTIMOS,
        COL_PRECIO_OFERTA_CENTIMOS,
        COL_STOCK,
        COL_IMAGEN_KEY,
        COL_ES_OFERTA,
        COL_ACTIVO
    )

    fun uriProducto(id: Long): Uri = ContentUris.withAppendedId(URI_PRODUCTOS, id)

    fun uriImagen(id: Long): Uri = Uri.withAppendedPath(uriProducto(id), "imagen")
}
