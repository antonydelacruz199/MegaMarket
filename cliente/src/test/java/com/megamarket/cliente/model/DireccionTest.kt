package com.megamarket.cliente.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DireccionTest {

    private val valida = Direccion(
        departamento = "Lima",
        provincia = "Lima",
        distrito = "Miraflores",
        direccion = "Av. Larco 123",
        telefono = "987 654 321"
    )

    @Test
    fun direccionCompleta_noTieneError() {
        assertNull(valida.error())
    }

    @Test
    fun camposVacios_devuelvenElPrimerError() {
        assertEquals("Ingresa el departamento", valida.copy(departamento = " ").error())
        assertEquals("Ingresa la provincia", valida.copy(provincia = "").error())
        assertEquals("Ingresa el distrito", valida.copy(distrito = "").error())
        assertEquals("Ingresa la dirección", valida.copy(direccion = "").error())
    }

    @Test
    fun telefono_requiereAlMenosSeisDigitos() {
        assertEquals("Ingresa un teléfono válido", valida.copy(telefono = "12-34").error())
        assertNull(valida.copy(telefono = "123456").error())
    }
}
