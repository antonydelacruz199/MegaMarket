package com.megamarket.picker.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccesoCamaraTest {

    @Test
    fun conCamaraSinPermisoSoloPidePermisoAlAbrir() {
        val estado = estadoAlAbrir(tieneCamara = true, permisoConcedido = false)
        assertTrue(estado.pedirPermiso)
        assertFalse(estado.camaraActiva)
        assertFalse(estado.entradaManual)
        assertTrue(estado.decidido)
    }

    @Test
    fun permisoDenegadoHabilitaEntradaManual() {
        val estado = estadoTrasPermiso(concedido = false)
        assertTrue(estado.entradaManual)
        assertFalse(estado.camaraActiva)
        assertFalse(estado.pedirPermiso)
        assertEquals(MotivoManual.PERMISO_DENEGADO, estado.motivo)
    }

    @Test
    fun sinCamaraNoPidePermiso() {
        val estado = estadoAlAbrir(tieneCamara = false, permisoConcedido = false)
        assertFalse(estado.pedirPermiso)
        assertTrue(estado.entradaManual)
        assertEquals(MotivoManual.SIN_CAMARA, estado.motivo)
    }

    @Test
    fun permisoConcedidoAbreLaCamara() {
        val estado = estadoTrasPermiso(concedido = true)
        assertTrue(estado.camaraActiva)
        assertFalse(estado.entradaManual)
    }
}
