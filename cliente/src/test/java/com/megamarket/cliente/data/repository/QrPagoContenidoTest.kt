package com.megamarket.cliente.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrPagoContenidoTest {

    @Test
    fun parseaQrDeServidor() {
        val contenido = """
            MEGAMARKET-PAGO
            v=1
            pedido=11111111-1111-4111-8111-111111111111
            pago=22222222-2222-4222-8222-222222222222
            ref=MM-REF-ABC
            monto_centimos=1500
            moneda=PEN
            modalidad=CONTRAENTREGA
            generado=2026-11-19T12:00:00.000Z
            estado=PENDIENTE
        """.trimIndent()
        val qr = parsearQrPago(contenido)
        assertNotNull(qr)
        assertEquals("11111111-1111-4111-8111-111111111111", qr!!.pedidoId)
        assertEquals("22222222-2222-4222-8222-222222222222", qr.pagoId)
        assertEquals("MM-REF-ABC", qr.referencia)
        assertEquals(1500L, qr.montoCentimos)
        assertEquals("CONTRAENTREGA", qr.modalidad)
    }

    @Test
    fun rechazaContenidoAjeno() {
        assertNull(parsearQrPago("otro-codigo"))
        assertNull(parsearQrPago("MEGAMARKET-PAGO\nv=1\npedido=\npago="))
    }
}
