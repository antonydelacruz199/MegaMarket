package com.megamarket.app.data.mapper

import com.google.gson.Gson
import com.megamarket.app.data.local.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCatalogoMapperUbicacionTest {

    private val gson = Gson()

    @Test
    fun actualizarIncluyeCodigoYPasilloEnJson() {
        val entidad = Producto(
            id = 1,
            remoteId = "prod-1",
            nombre = "Leche",
            marca = "A",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 100,
            precioOfertaCentimos = null,
            stock = 5,
            imagenKey = "123.jpg",
            esOferta = false,
            activo = true,
            remoteVersion = 3,
            codigoBarras = "770001",
            pasillo = "A-1"
        )
        val body = entidad.aActualizarProductoRequest("cat-1", "uuid-1")
        val json = gson.toJson(body)
        assertTrue(json.contains("\"codigoBarras\":\"770001\""))
        assertTrue(json.contains("\"pasillo\":\"A-1\""))
        // vista previa local: omitir imagenKey (null) para no borrar la remota
        assertFalse(json.contains("imagenKey"))
        assertEquals(3L, body.version)
    }

    @Test
    fun crearIncluyeCodigoYPasilloEnJson() {
        val entidad = Producto(
            id = 1,
            nombre = "Leche",
            marca = "A",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 100,
            precioOfertaCentimos = null,
            stock = 5,
            imagenKey = "",
            esOferta = false,
            activo = true,
            codigoBarras = "770002",
            pasillo = "B 2"
        )
        val json = gson.toJson(entidad.aCrearProductoRequest("cat-1", "uuid-2"))
        assertTrue(json.contains("\"codigoBarras\":\"770002\""))
        assertTrue(json.contains("\"pasillo\":\"B 2\""))
    }
}
