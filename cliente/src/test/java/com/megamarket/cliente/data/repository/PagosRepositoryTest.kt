package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.ConfirmarPagoResponse
import com.megamarket.cliente.data.remote.GenerarQrResponse
import com.megamarket.cliente.data.remote.PagoPedidoRemotoDto
import com.megamarket.cliente.data.remote.PagoResponse
import com.megamarket.cliente.data.remote.PedidoEstadoPagoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PagosRepositoryTest {

    private fun repositorio(api: ApiPagosFake, owner: String? = "cliente-1") =
        PagosRepository(api = api, usuarioRemotoId = { owner }, io = Dispatchers.Unconfined)

    @Test
    fun generarQrMapeaPagoYQrConEtiquetaSimulacion() = runBlocking {
        val api = ApiPagosFake()
        val exito = repositorio(api).generarQr(ApiPagosFake.PEDIDO) as ResultadoPagos.Exito
        assertEquals("QR_GENERADO", exito.valor.pago.estado)
        assertEquals(1_500L, exito.valor.pago.montoCentimos)
        assertFalse(exito.valor.pago.confirmado)
        val qr = exito.valor.qr
        assertNotNull(qr)
        assertEquals("SIMULACIÓN", qr!!.etiqueta)
        assertEquals("MEGAMARKET-SIM|${ApiPagosFake.PAGO}", qr.contenido)
        assertEquals(1_500L, qr.importeCentimos)
        assertEquals(1, api.generados)
    }

    @Test
    fun generarQrConPagoYaConfirmadoNoTraeQr() = runBlocking {
        val api = ApiPagosFake()
        api.qr = Response.success(
            GenerarQrResponse(
                pago = ApiPagosFake.pagoDto("CONFIRMADO").copy(qrContenido = null),
                yaConfirmado = true
            )
        )
        val exito = repositorio(api).generarQr(ApiPagosFake.PEDIDO) as ResultadoPagos.Exito
        assertTrue(exito.valor.pago.confirmado)
        assertNull(exito.valor.qr)
    }

    @Test
    fun confirmarDevuelveSoloLoQueDiceElServidor() = runBlocking {
        val api = ApiPagosFake()
        val exito = repositorio(api).confirmar(ApiPagosFake.PAGO, "uuid-op") as ResultadoPagos.Exito
        assertTrue(exito.valor.pago.confirmado)
        assertEquals("PAGADO", exito.valor.pedidoEstado)
        assertEquals(8_500L, exito.valor.saldoClienteCentimos)
        assertEquals(listOf("uuid-op"), api.uuidsConfirmacion)
    }

    @Test
    fun confirmarRechazaRespuestaQueNoDeclaraConfirmado() = runBlocking {
        val api = ApiPagosFake()
        api.confirmacion = Response.success(
            ConfirmarPagoResponse(
                pago = ApiPagosFake.pagoDto("QR_GENERADO"),
                pedido = PedidoEstadoPagoDto(ApiPagosFake.PEDIDO, "CONFIRMADO")
            )
        )
        val error = repositorio(api).confirmar(ApiPagosFake.PAGO, "uuid-op") as ResultadoPagos.Error
        assertEquals("PAGO_NO_CONFIRMADO", error.codigo)
        assertEquals(MensajesPagoCliente.NO_CONFIRMADO, error.mensaje)
    }

    @Test
    fun conflictoHttpConservaMensajeYCodigoDelServidor() = runBlocking {
        val api = ApiPagosFake()
        api.confirmacion = ApiPagosFake.errorHttp(
            409,
            """{"error":"El QR simulado expiró; genera uno nuevo","codigo":"QR_EXPIRADO"}"""
        )
        val error = repositorio(api).confirmar(ApiPagosFake.PAGO, "uuid-op") as ResultadoPagos.Error
        assertEquals(TipoErrorSustitucion.CONFLICTO, error.tipo)
        assertEquals("QR_EXPIRADO", error.codigo)
        assertTrue(error.mensaje.contains("expiró"))
    }

    @Test
    fun errorDeRedSeReportaComoRed() = runBlocking {
        val api = ApiPagosFake().apply { fallosRedConfirmacion = 1 }
        val error = repositorio(api).confirmar(ApiPagosFake.PAGO, "uuid-op") as ResultadoPagos.Error
        assertEquals(TipoErrorSustitucion.RED, error.tipo)
    }

    @Test
    fun sinSesionNoLlamaALaApi() = runBlocking {
        val api = ApiPagosFake()
        val error = repositorio(api, owner = null).generarQr(ApiPagosFake.PEDIDO) as ResultadoPagos.Error
        assertEquals(TipoErrorSustitucion.SESION, error.tipo)
        assertEquals(0, api.generados)
    }

    @Test
    fun obtenerPorPedidoMapeaPago() = runBlocking {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("CONFIRMADO"), pedidoId = ApiPagosFake.PEDIDO)
        )
        val exito = repositorio(api).obtenerPorPedido(ApiPagosFake.PEDIDO) as ResultadoPagos.Exito
        assertTrue(exito.valor.confirmado)
        assertEquals("SIM-OP-1", exito.valor.numeroOperacionBanco)
    }

    @Test
    fun pagoSinIdOEstadoSeDescarta() {
        assertNull(PagoPedidoRemotoDto(id = null, estado = "CONFIRMADO").aPagoCliente())
        assertNull(PagoPedidoRemotoDto(id = "x", estado = " ").aPagoCliente())
    }
}
