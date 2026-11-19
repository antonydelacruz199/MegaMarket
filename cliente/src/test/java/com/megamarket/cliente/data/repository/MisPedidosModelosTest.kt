package com.megamarket.cliente.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MisPedidosModelosTest {

    @Test
    fun filtrosMapeanEstadosReales() {
        assertTrue(FiltroMisPedidos.PENDIENTES_PAGO.coincide("CONFIRMADO", "ONLINE"))
        assertFalse(FiltroMisPedidos.PENDIENTES_PAGO.coincide("CONFIRMADO", "CONTRAENTREGA"))
        assertFalse(FiltroMisPedidos.PENDIENTES_PAGO.coincide("CONFIRMADO", null))
        assertTrue(FiltroMisPedidos.EN_PREPARACION.coincide("EN_PICKING", null))
        assertTrue(FiltroMisPedidos.EN_PREPARACION.coincide("PAGADO", "ONLINE"))
        assertTrue(FiltroMisPedidos.LISTOS.coincide("LISTO", null))
        assertTrue(FiltroMisPedidos.EN_CAMINO.coincide("EN_CAMINO", null))
        assertTrue(FiltroMisPedidos.ENTREGADOS.coincide("ENTREGADO", null))
        assertTrue(FiltroMisPedidos.CANCELADOS.coincide("CANCELADO", null))
    }

    @Test
    fun progresoContraentregaOmitePagado() {
        val detalle = PedidoDetalleCliente(
            id = "p1",
            fecha = null,
            totalCentimos = 100,
            estado = "EN_PICKING",
            franjaEntregaId = null,
            franjaEtiqueta = null,
            version = 1,
            lineas = emptyList(),
            direccion = null,
            pago = PagoPedidoCliente(
                modalidad = "CONTRAENTREGA",
                estado = "PENDIENTE",
                montoCentimos = 100,
                medioPago = "QR",
                numeroOperacionBanco = null,
                fechaConfirmacion = null
            ),
            sustituciones = emptyList()
        )
        val etapas = detalle.etapasProgreso()
        assertFalse(etapas.any { it.nombre == "PAGADO" })
        assertTrue(etapas.first { it.nombre == "CONFIRMADO" }.completada)
        assertTrue(etapas.first { it.nombre == "EN_PICKING" }.actual)
        assertFalse(etapas.first { it.nombre == "LISTO" }.completada)
        assertFalse(etapas.first { it.nombre == "LISTO" }.actual)
    }

    @Test
    fun progresoOnlineIncluyePagado() {
        val detalle = PedidoDetalleCliente(
            id = "p1",
            fecha = null,
            totalCentimos = 100,
            estado = "PAGADO",
            franjaEntregaId = null,
            franjaEtiqueta = "Lun 18 nov · 10:00 – 12:00",
            version = 1,
            lineas = emptyList(),
            direccion = null,
            pago = PagoPedidoCliente(
                modalidad = "ONLINE",
                estado = "CONFIRMADO",
                montoCentimos = 100,
                medioPago = "QR",
                numeroOperacionBanco = "OP1",
                fechaConfirmacion = "2026-01-01"
            ),
            sustituciones = emptyList()
        )
        assertEquals("Lun 18 nov · 10:00 – 12:00", detalle.franjaVisible)
        assertEquals("Pago en línea", detalle.modalidadVisible)
        val etapas = detalle.etapasProgreso().map { it.nombre }
        assertEquals(
            listOf("CONFIRMADO", "PAGADO", "EN_PICKING", "LISTO", "EN_CAMINO", "ENTREGADO"),
            etapas
        )
    }
}
