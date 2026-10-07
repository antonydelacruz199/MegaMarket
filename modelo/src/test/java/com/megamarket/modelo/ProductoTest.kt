package com.megamarket.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductoTest {

    private fun producto(
        precio: Long = 1_000,
        oferta: Long? = null,
        esOferta: Boolean = false,
        stock: Int = 10
    ) = Producto(
        id = 1,
        nombre = "Arroz Costeño",
        marca = "Costeño",
        descripcion = "",
        categoriaId = 1,
        precioCentimos = precio,
        precioOfertaCentimos = oferta,
        stock = stock,
        imagenKey = "",
        esOferta = esOferta
    )

    @Test
    fun ofertaValida_requiereMarcaYPrecioMenor() {
        assertTrue(producto(oferta = 800, esOferta = true).ofertaValida)
        assertFalse(producto(oferta = 800, esOferta = false).ofertaValida)
        assertFalse(producto(oferta = null, esOferta = true).ofertaValida)
        assertFalse(producto(oferta = 1_000, esOferta = true).ofertaValida)
        assertFalse(producto(oferta = 1_200, esOferta = true).ofertaValida)
    }

    @Test
    fun precioVigente_usaOfertaSoloSiEsValida() {
        assertEquals(800L, producto(oferta = 800, esOferta = true).precioVigenteCentimos)
        assertEquals(1_000L, producto(oferta = 800, esOferta = false).precioVigenteCentimos)
        assertEquals(1_000L, producto(oferta = 1_500, esOferta = true).precioVigenteCentimos)
    }

    @Test
    fun stockBajo_esDeUnoACinco_yCeroEsAgotado() {
        assertFalse(producto(stock = 0).stockBajo)
        assertTrue(producto(stock = 0).agotado)
        assertTrue(producto(stock = 1).stockBajo)
        assertTrue(producto(stock = Producto.STOCK_BAJO_MAXIMO).stockBajo)
        assertFalse(producto(stock = Producto.STOCK_BAJO_MAXIMO + 1).stockBajo)
    }
}
