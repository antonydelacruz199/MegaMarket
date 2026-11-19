package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.BilleteraSaldoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class BilleteraRepositoryTest {

    private fun repositorio(api: ApiPagosFake) =
        BilleteraRepository(api = api, usuarioRemotoId = { "cliente-1" }, io = Dispatchers.Unconfined)

    @Test
    fun cargaSaldoYMovimientos() = runBlocking {
        val exito = repositorio(ApiPagosFake()).cargar() as ResultadoPagos.Exito
        assertEquals(8_500L, exito.valor.cuenta.saldoCentimos)
        assertEquals(10_000L, exito.valor.saldoInicialCentimos)
        assertEquals(1, exito.valor.movimientos.size)
        val mov = exito.valor.movimientos.single()
        assertEquals("COBRO_PEDIDO", mov.tipo)
        assertFalse(mov.esCredito)
        assertEquals(-1_500L, mov.montoConSigno)
        assertEquals(8_500L, mov.saldoPosteriorCentimos)
    }

    @Test
    fun movimientoCreditoSumaAlSaldo() = runBlocking {
        val api = ApiPagosFake()
        val original = (api.movimientos.body()!!)
        api.movimientos = Response.success(
            original.copy(movimientos = original.movimientos!!.map { it.copy(sentido = "CREDITO") })
        )
        val exito = repositorio(api).listarMovimientos() as ResultadoPagos.Exito
        val mov = exito.valor.movimientos.single()
        assertTrue(mov.esCredito)
        assertEquals(1_500L, mov.montoConSigno)
    }

    @Test
    fun saldoSinCuentaEsRespuestaInvalida() = runBlocking {
        val api = ApiPagosFake()
        api.saldo = Response.success(BilleteraSaldoResponse(cuenta = null))
        val error = repositorio(api).obtenerSaldo() as ResultadoPagos.Error
        assertEquals(TipoErrorSustitucion.SERVIDOR, error.tipo)
    }

    @Test
    fun errorEnSaldoCortaLaCarga() = runBlocking {
        val api = ApiPagosFake()
        api.saldo = ApiPagosFake.errorHttp(403, """{"error":"Solo cliente o administrador","codigo":"PERMISO_DENEGADO"}""")
        val error = repositorio(api).cargar() as ResultadoPagos.Error
        assertEquals(TipoErrorSustitucion.PROHIBIDO, error.tipo)
        assertEquals("PERMISO_DENEGADO", error.codigo)
    }
}
