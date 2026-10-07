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
        categoriaId = 1L,
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
        assertEquals(0, producto.descuentoPorcentaje)
        assertNull(producto.precioOfertaCentimos)
        assertNull(producto.remoteId)
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
    fun categoria_esObligatoria() {
        assertEquals("Selecciona una categoría", mensaje(valido.copy(categoriaId = 0L)))
    }

    @Test
    fun remoteId_seConservaAlValidar() {
        val resultado = ValidacionProducto.validar(
            valido.copy(remoteId = "550e8400-e29b-41d4-a716-446655440000")
        )
        val producto = (resultado as ValidacionProducto.Resultado.Valido).producto
        assertEquals("550e8400-e29b-41d4-a716-446655440000", producto.remoteId)
    }

    @Test
    fun descuento_derivaPrecioOferta() {
        val conOferta = valido.copy(esOferta = true, descuentoPorcentaje = "20")
        val producto = (ValidacionProducto.validar(conOferta) as ValidacionProducto.Resultado.Valido).producto
        assertEquals(20, producto.descuentoPorcentaje)
        assertEquals(384L, producto.precioOfertaCentimos)
    }

    @Test
    fun descuento_validaRango0a100() {
        val conOferta = valido.copy(esOferta = true)
        assertEquals("Ingresa el porcentaje de descuento", mensaje(conOferta.copy(descuentoPorcentaje = "")))
        assertEquals("Ingresa un porcentaje de descuento válido", mensaje(conOferta.copy(descuentoPorcentaje = "x")))
        assertEquals("El descuento debe ser mayor a 0 cuando hay oferta", mensaje(conOferta.copy(descuentoPorcentaje = "0")))
        assertEquals("El descuento debe ser menor a 100", mensaje(conOferta.copy(descuentoPorcentaje = "100")))
        assertEquals("El descuento debe estar entre 0 y 100", mensaje(conOferta.copy(descuentoPorcentaje = "150")))
        assertNull(mensaje(conOferta.copy(descuentoPorcentaje = "15")))
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
