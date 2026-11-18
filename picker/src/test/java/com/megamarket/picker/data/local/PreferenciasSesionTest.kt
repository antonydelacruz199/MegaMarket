package com.megamarket.picker.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PreferenciasSesionTest {

    @Test
    fun elArchivoNoCoincideConAdministradorNiCliente() {
        assertEquals("megamarket_sesion_picker", NOMBRE_PREFERENCIAS_SESION)
        assertNotEquals("megamarket_sesion_admin", NOMBRE_PREFERENCIAS_SESION)
        assertNotEquals("megamarket_sesion_cliente", NOMBRE_PREFERENCIAS_SESION)
    }
}
