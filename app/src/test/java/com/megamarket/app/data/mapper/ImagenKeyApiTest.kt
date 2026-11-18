package com.megamarket.app.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagenKeyApiTest {

    @Test
    fun actualizarOmiteVistaPreviaLocalYConservaClaveRemota() {
        val remota = "productos/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee/" +
            "11111111-2222-4333-8444-555555555555.jpg"
        assertEquals("", imagenKeyParaActualizar(null))
        assertEquals("", imagenKeyParaActualizar("  "))
        assertNull(imagenKeyParaActualizar("1710000000000.jpg"))
        assertNull(imagenKeyParaActualizar("/storage/foto.jpg"))
        assertEquals(remota, imagenKeyParaActualizar(remota))
    }
}
