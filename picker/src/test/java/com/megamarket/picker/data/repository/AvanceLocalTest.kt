package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvanceLocalTest {

    @Test
    fun evitaCantidadesDuplicadasSobreLaSolicitada() {
        assertTrue(cabeEscaneo(armadaConfirmada = 1, pendienteLocal = 0, solicitada = 2, cantidad = 1))
        assertFalse(cabeEscaneo(armadaConfirmada = 1, pendienteLocal = 1, solicitada = 2, cantidad = 1))
        assertFalse(cabeEscaneo(armadaConfirmada = 2, pendienteLocal = 0, solicitada = 2, cantidad = 1))
    }

    @Test
    fun erroresDefinitivosNoCuentanComoPendienteLocal() {
        val ops = listOf(
            OperacionPickingEntity(
                uuidOperacion = "a",
                ownerId = "p",
                pedidoId = "1",
                detalleId = "d",
                operacion = OperacionPicking.ESCANEAR,
                codigoBarras = "1",
                cantidad = 1,
                estado = OperacionPicking.PENDIENTE,
                fechaCreacion = 1L
            ),
            OperacionPickingEntity(
                uuidOperacion = "b",
                ownerId = "p",
                pedidoId = "1",
                detalleId = "d",
                operacion = OperacionPicking.ESCANEAR,
                codigoBarras = "1",
                cantidad = 1,
                estado = OperacionPicking.ERROR,
                resultado = "CANTIDAD_EXCEDIDA",
                fechaCreacion = 2L
            )
        )
        assertEquals(1, cantidadPendienteEscaneos(ops))
        assertFalse(hayFaltantePendiente(ops))
    }
}
