package com.megamarket.app.model

import com.megamarket.app.model.estado.ProductoFormUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionProductoTest {

    private val valido = ProductoFormUiState(
        nombre = "Arroz Costeño",
        marca = "Costeño",
        categoria = "1",
        precio = "4.80",
        stock = "10"
    )

    private fun mensaje(formulario: ProductoFormUiState): String? =
        (ValidacionProducto.validar(formulario) as? ValidacionProducto.Resultado.Invalido)?.mensaje

    @Test
    fun formularioCompleto_esValido() {
        val resultado = ValidacionProducto.validar(valido)
        assertTrue(resultado is ValidacionProducto.Resultado.Valido)
        val producto = (resultado as ValidacionProducto.Resultado.Valido).producto
        assertEquals(480L, producto.precioCentimos)
        assertEquals(10, producto.stock)
        assertNull(producto.precioOfertaCentimos)
    }

    @Test
    fun nombreYMarca_sonObligatorios() {
        assertEquals("Ingresa el nombre del producto", mensaje(valido.copy(nombre = "  ")))
        assertEquals("Ingresa la marca del producto", mensaje(valido.copy(marca = "")))
    }

    @Test
    fun precio_debeSerMayorACero() {
        assertEquals("Ingresa un precio válido", mensaje(valido.copy(precio = "abc")))
        assertEquals("El precio debe ser mayor a 0", mensaje(valido.copy(precio = "0")))
    }

    @Test
    fun stock_debeSerEnteroNoNegativo() {
        assertEquals("El stock debe ser un número entero", mensaje(valido.copy(stock = "2.5")))
        assertEquals("El stock no puede ser negativo", mensaje(valido.copy(stock = "-1")))
        assertNull(mensaje(valido.copy(stock = "0")))
    }

    @Test
    fun categoria_debeSerNumerica() {
        assertEquals(
            "La categoría debe ser un número entero positivo",
            mensaje(valido.copy(categoria = "abc"))
        )
    }

    @Test
    fun oferta_esObligatoriaPositivaYMenorAlPrecio() {
        val conOferta = valido.copy(esOferta = true)
        assertEquals("Ingresa el precio de oferta", mensaje(conOferta.copy(precioOferta = "")))
        assertEquals("Ingresa un precio de oferta válido", mensaje(conOferta.copy(precioOferta = "x")))
        assertEquals("El precio de oferta debe ser mayor a 0", mensaje(conOferta.copy(precioOferta = "0")))
        assertEquals("La oferta debe ser menor al precio", mensaje(conOferta.copy(precioOferta = "4.80")))
        assertNull(mensaje(conOferta.copy(precioOferta = "3.90")))
    }

    @Test
    fun aCentimos_noPierdePrecision() {
        assertEquals(480L, "4.80".aCentimos())
        assertEquals(480L, "4,8".aCentimos())
        assertEquals(1L, "0.005".aCentimos())
        assertEquals(29L, "0.29".aCentimos())
        assertNull("-1".aCentimos())
        assertNull("".aCentimos())
    }
}
