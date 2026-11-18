package com.megamarket.picker.ui.navigation

import com.megamarket.picker.model.RolOperativo
import org.junit.Assert.assertEquals
import org.junit.Test

class RutaTest {

    @Test
    fun pickerYRepartidorTienenRutasDistintas() {
        assertEquals("picker", rutaDeRol(RolOperativo.PICKER))
        assertEquals("repartidor", rutaDeRol(RolOperativo.REPARTIDOR))
        assertEquals(Ruta.Sesion.ruta, "sesion")
        assertEquals("preparacion/abc", Ruta.Preparacion.crear("abc"))
        assertEquals("preparacion/abc/escaner/det-1", Ruta.Escaner.crear("abc", "det-1"))
        assertEquals("preparacion/abc/sustitucion/det-1", Ruta.Sustitucion.crear("abc", "det-1"))
    }
}
