package com.megamarket.app.data.local.dao

import com.megamarket.app.modelo.Producto

/**
 * Contrato de acceso local a productos.
 * Las anotaciones de Room se agregarán al conectar la base de datos.
 */
interface ProductoDao {
    fun obtenerActivos(): List<Producto>
}
