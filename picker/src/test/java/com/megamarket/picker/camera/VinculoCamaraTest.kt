package com.megamarket.picker.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinculoCamaraTest {

    @Test
    fun cerrarLiberaCamaraLectorYEjecutorUnaVez() {
        var desenlace = 0
        var lector = 0
        var ejecutor = 0
        val vinculo = VinculoCamara(
            desenlazar = { desenlace += 1 },
            cerrarLector = { lector += 1 },
            cerrarEjecutor = { ejecutor += 1 }
        )
        assertTrue(vinculo.marcarAbierto())
        assertTrue(vinculo.abierto)
        vinculo.cerrar()
        vinculo.cerrar()
        assertEquals(1, desenlace)
        assertEquals(1, lector)
        assertEquals(1, ejecutor)
        assertTrue(vinculo.cerrado)
        assertFalse(vinculo.abierto)
    }

    @Test
    fun cerrarSinLlegarAAbrirTambienLiberaLosRecursos() {
        var liberados = 0
        val vinculo = VinculoCamara(
            desenlazar = { liberados += 1 },
            cerrarLector = { liberados += 1 },
            cerrarEjecutor = { liberados += 1 }
        )
        vinculo.cerrar()
        assertEquals(3, liberados)
        assertFalse(vinculo.marcarAbierto())
    }
}
