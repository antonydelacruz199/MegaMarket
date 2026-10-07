package com.megamarket.cliente.model

import com.megamarket.modelo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineaCarritoTest {

    private fun producto(id: Long, precio: Long, oferta: Long? = null, stock: Int = 10) = Producto(
        id = id,
        nombre = "Producto $id",
        marca = "Marca",
        descripcion = "",
        categoriaId = 1,
        precioCentimos = precio,
        precioOfertaCentimos = oferta,
        stock = stock,
        imagenKey = "",
        esOferta = oferta != null
    )

    @Test
    fun total_sumaSubtotalesConPrecioVigente() {
        val lineas = listOf(
            LineaCarrito(producto(1, precio = 480), cantidad = 2),
            LineaCarrito(producto(2, precio = 1_000, oferta = 750), cantidad = 3)
        )
        assertEquals(480L * 2 + 750L * 3, lineas.totalCentimos())
    }

    @Test
    fun total_ignoraProductosAgotados() {
        val lineas = listOf(
            LineaCarrito(producto(1, precio = 480), cantidad = 1),
            LineaCarrito(producto(2, precio = 999, stock = 0), cantidad = 2)
        )
        assertEquals(480L, lineas.totalCentimos())
    }

    @Test
    fun lineaQueSuperaElStock_noEsComprable() {
        val linea = LineaCarrito(producto(1, precio = 480, stock = 2), cantidad = 3)
        assertTrue(linea.excedeStock)
        assertFalse(linea.comprable)
        assertTrue(linea.copy(cantidad = 2).comprable)
    }
}
