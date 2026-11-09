package com.megamarket.cliente.model

import org.junit.Assert.*
import org.junit.Test

class FrescuraCatalogoTest {
    @Test fun permiteHastaUnaHoraInclusiva() {
        assertTrue(FrescuraCatalogo.vigente(1000, 1000 + 3599999))
        assertTrue(FrescuraCatalogo.vigente(1000, 1000 + 3600000))
        assertFalse(FrescuraCatalogo.vigente(1000, 1000 + 3600001))
    }
    @Test fun bloqueaSinDatosOConRelojRetrocedido() {
        assertFalse(FrescuraCatalogo.vigente(null, 1000))
        assertFalse(FrescuraCatalogo.vigente(1000, 999))
    }
    @Test fun elRelojDelTelefonoNoExtiendeElPlazo() {
        assertFalse(FrescuraCatalogo.vigenteConReloj(1000,2000,1000,3601001,3,3))
        assertFalse(FrescuraCatalogo.vigenteConReloj(1000,2000,1000,2000,3,4))
        assertFalse(FrescuraCatalogo.vigenteConReloj(1000,2000,1000,999,3,3))
        assertTrue(FrescuraCatalogo.vigenteConReloj(1000,2000,1000,2000,3,3))
    }
}
