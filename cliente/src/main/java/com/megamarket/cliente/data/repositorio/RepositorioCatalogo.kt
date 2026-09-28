package com.megamarket.cliente.data.repositorio

import android.content.ContentResolver
import android.database.Cursor
import com.megamarket.modelo.ContratoCatalogo
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepositorioCatalogo(
    private val resolver: ContentResolver
) {
    suspend fun obtenerActivos(): List<Producto> = withContext(Dispatchers.IO) {
        leerActivos()
    }

    suspend fun obtenerPorId(id: Long): Producto? = withContext(Dispatchers.IO) {
        leerPorId(id)
    }

    fun leerActivos(): List<Producto> = leer(ContratoCatalogo.URI_PRODUCTOS)

    fun leerPorId(id: Long): Producto? = leer(ContratoCatalogo.uriProducto(id)).firstOrNull()

    private fun leer(uri: android.net.Uri): List<Producto> {
        val cursor = try {
            resolver.query(uri, ContratoCatalogo.COLUMNAS, null, null, null)
        } catch (error: SecurityException) {
            throw IllegalStateException(MENSAJE_ERROR, error)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException(MENSAJE_ERROR, error)
        } ?: throw IllegalStateException(MENSAJE_ERROR)

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
