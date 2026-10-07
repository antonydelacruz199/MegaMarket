package com.megamarket.cliente.model

import com.megamarket.modelo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReglasCheckoutTest {

    private fun producto(
        id: Long,
        nombre: String,
        precio: Long,
        oferta: Long? = null,
        stock: Int = 10,
        activo: Boolean = true
    ) = Producto(
        id = id,
        nombre = nombre,
        marca = "Marca",
        descripcion = "",
        categoriaId = 1,
        precioCentimos = precio,
        precioOfertaCentimos = oferta,
        stock = stock,
        imagenKey = "",
        esOferta = oferta != null,
        activo = activo
    )

    @Test
    fun cantidadMayorAlStock_rechazaElCheckoutSinAjustar() {
        val catalogo = mapOf(1L to producto(1, "Arroz Costeño", precio = 480, stock = 2))
        val error = assertThrows(CheckoutException::class.java) {
            armarDetallesPedido(listOf(ProductoSolicitado(1, 5)), catalogo)
        }
        assertEquals("El stock de Arroz Costeño cambió. Disponible: 2 unidades.", error.message)
        // El descuento atómico del admin tampoco aplicaría: stock 2 < 3.
        assertNull(StockLocal.stockTrasDescuento(2, 3))
    }

    @Test
    fun stockDeUnaUnidad_usaSingular() {
        val catalogo = mapOf(1L to producto(1, "Leche", precio = 400, stock = 1))
        val error = assertThrows(CheckoutException::class.java) {
            armarDetallesPedido(listOf(ProductoSolicitado(1, 2)), catalogo)
        }
        assertEquals("El stock de Leche cambió. Disponible: 1 unidad.", error.message)
    }

    @Test
    fun carritoVacio_esRechazado() {
        val error = assertThrows(CheckoutException::class.java) {
            armarDetallesPedido(emptyList(), emptyMap())
        }
        assertEquals("Tu carrito está vacío", error.message)
    }

    @Test
    fun productoInactivoOInexistente_informaLosIdsParaQuitarlos() {
        val catalogo = mapOf(1L to producto(1, "Aceite", precio = 900, activo = false))
        val error = assertThrows(CheckoutException::class.java) {
            armarDetallesPedido(listOf(ProductoSolicitado(1, 1), ProductoSolicitado(7, 1)), catalogo)
        }
        assertEquals(listOf(1L, 7L), error.productosNoDisponibles)
    }

    @Test
    fun productoAgotado_esRechazado() {
        val catalogo = mapOf(1L to producto(1, "Azúcar", precio = 500, stock = 0))
        val error = assertThrows(CheckoutException::class.java) {
            armarDetallesPedido(listOf(ProductoSolicitado(1, 1)), catalogo)
        }
        assertEquals("Azúcar se agotó. Quítalo del carrito para continuar.", error.message)
    }

    @Test
    fun detalle_guardaNombreYPrecioVigenteDelMomento() {
        val catalogo = mapOf(
            1L to producto(1, "Arroz Costeño", precio = 480),
            2L to producto(2, "Aceite Primor", precio = 1_200, oferta = 990)
        )
        val detalles = armarDetallesPedido(
            listOf(ProductoSolicitado(1, 2), ProductoSolicitado(2, 1)),
            catalogo
        )

        assertEquals("Arroz Costeño", detalles[0].nombreProducto)
        assertEquals(480L, detalles[0].precioUnitarioCentimos)
        assertEquals(960L, detalles[0].subtotalCentimos)
        assertEquals(990L, detalles[1].precioUnitarioCentimos)
        assertEquals(990L, detalles[1].subtotalCentimos)
    }
}
