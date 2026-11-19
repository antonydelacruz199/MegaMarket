package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.PagoPedidoRemotoDto
import com.megamarket.cliente.data.remote.QrSimuladoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagoModelosTest {

    private fun detalle(estadoPedido: String, modalidad: String?, estadoPago: String?) =
        PedidoDetalleCliente(
            id = "p1",
            fecha = null,
            totalCentimos = 1_500,
            estado = estadoPedido,
            franjaEntregaId = null,
            franjaEtiqueta = null,
            version = 1,
            lineas = emptyList(),
            direccion = null,
            pago = if (modalidad == null) null else PagoPedidoCliente(
                id = "pg1",
                modalidad = modalidad,
                estado = estadoPago,
                montoCentimos = 1_500,
                medioPago = "QR_SIMULADO",
                numeroOperacionBanco = null,
                fechaConfirmacion = null
            ),
            sustituciones = emptyList()
        )

    @Test
    fun soloOnlineSinConfirmarPuedePagarConQr() {
        assertTrue(detalle("CONFIRMADO", "ONLINE", "PENDIENTE").puedePagarConQr)
        assertTrue(detalle("CONFIRMADO", "ONLINE", "QR_GENERADO").puedePagarConQr)
        assertFalse(detalle("PAGADO", "ONLINE", "CONFIRMADO").puedePagarConQr)
        assertFalse(detalle("CONFIRMADO", "CONTRAENTREGA", "PENDIENTE").puedePagarConQr)
        assertTrue(detalle("LISTO", "CONTRAENTREGA", "PENDIENTE").puedePagarConQr)
        assertTrue(detalle("EN_CAMINO", "CONTRAENTREGA", "PENDIENTE").puedePagarConQr)
        assertFalse(detalle("CANCELADO", "ONLINE", "PENDIENTE").puedePagarConQr)
        assertFalse(detalle("CONFIRMADO", null, null).puedePagarConQr)
    }

    @Test
    fun pagoRemotoSeNormalizaAMayusculas() {
        val pago = PagoPedidoRemotoDto(
            id = "pg1",
            pedidoId = "p1",
            montoCentimos = 900,
            modalidad = "online",
            estado = "qr_generado",
            qrContenido = "SIM|x",
            simulacion = true
        ).aPagoCliente()!!
        assertEquals("ONLINE", pago.modalidad)
        assertEquals("QR_GENERADO", pago.estado)
        assertEquals("PEN", pago.moneda)
        assertFalse(pago.confirmado)
        assertTrue(pago.simulacion)
    }

    @Test
    fun qrUsaEtiquetaSimulacionPorDefectoYContenidoDelPago() {
        val pago = PagoPedidoRemotoDto(
            id = "pg1", estado = "QR_GENERADO", montoCentimos = 900, qrContenido = "SIM|desde-pago"
        ).aPagoCliente()!!
        val qr = QrSimuladoDto(contenido = null, etiqueta = null).aQrCliente(pago)!!
        assertEquals("SIM|desde-pago", qr.contenido)
        assertEquals("Código QR", qr.etiqueta)
        assertEquals(900L, qr.importeCentimos)
        assertNull(QrSimuladoDto(contenido = " ").aQrCliente(null))
    }

    @Test
    fun textoFechaHoraTolerarFechasInvalidas() {
        assertEquals("—", textoFechaHora(null))
        assertTrue(textoFechaHora("2030-06-15T12:00:00.000Z").contains("2030"))
        assertEquals("no-es-fecha", textoFechaHora("no-es-fecha"))
    }
}
