package com.megamarket.cliente.model

import com.megamarket.modelo.PrecioDescuento
import com.megamarket.modelo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Rf09DescuentoTest {

    @Test
    fun subtotalYTotal_conDescuentoPorcentaje() {
        val producto = Producto(
            id = 1,
            nombre = "Arroz",
            marca = "A",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 1000,
            descuentoPorcentaje = 20,
            stock = 10,
            imagenKey = "",
            esOferta = true
        )
        assertEquals(800L, producto.precioVigenteCentimos)
        val linea = LineaCarrito(producto, 3)
        assertEquals(2400L, linea.subtotalCentimos)
        assertEquals(2400L, listOf(linea).totalCentimos())
    }

    @Test
    fun rf10_cantidadNoSuperaStock() {
        val producto = Producto(
            id = 1,
            nombre = "Leche",
            marca = "G",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 400,
            stock = 2,
            imagenKey = "",
            esOferta = false
        )
        assertTrue(LineaCarrito(producto, 2).comprable)
        assertFalse(LineaCarrito(producto, 3).comprable)
        assertTrue(LineaCarrito(producto, 3).excedeStock)
    }

    @Test
    fun utilidadCentralizada() {
        assertEquals(800L, PrecioDescuento.precioConDescuentoCentimos(1000, 20))
    }
}
