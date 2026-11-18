package com.megamarket.picker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparacionTest {

    @Test
    fun agrupaEnElOrdenDelServidorYDejaSinPasilloAlFinal() {
        val grupos = agruparPorPasillo(
            listOf(
                linea("1", "A", "1"),
                linea("2", "B", "1"),
                linea("3", "C", "2"),
                linea("4", "D", null)
            )
        )
        assertEquals(listOf("1", "2", SIN_PASILLO), grupos.map { it.pasillo })
        assertEquals(listOf("A", "B"), grupos[0].lineas.map { it.nombre })
        assertEquals("D", grupos[2].lineas.single().nombre)
    }

    @Test
    fun laImagenUsaElProxyAutenticado() {
        val url = urlImagenProducto("http://10.0.2.2:8010", "prod-1")
        assertEquals("http://10.0.2.2:8010/api/medios/productos/prod-1?download=1", url)
        assertTrue(urlImagenProducto("http://10.0.2.2:8010/", "  ") == null)
    }

    private fun linea(id: String, nombre: String, pasillo: String?) = LineaPreparacion(
        id = id,
        productoId = "p$id",
        nombre = nombre,
        cantidad = 2,
        cantidadArmada = 0,
        estadoPicking = "PENDIENTE",
        pasillo = pasillo,
        imagenUrl = null
    )
}
