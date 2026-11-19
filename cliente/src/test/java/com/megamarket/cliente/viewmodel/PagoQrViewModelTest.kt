package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.megamarket.cliente.data.remote.PagoResponse
import com.megamarket.cliente.data.remote.PedidoEstadoPagoDto
import com.megamarket.cliente.data.repository.ApiPagosFake
import com.megamarket.cliente.data.repository.BilleteraRepository
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PagoQrViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun modelo(api: ApiPagosFake, pedido: String = ApiPagosFake.PEDIDO): PagoQrViewModel {
        val pagos = PagosRepository(api = api, usuarioRemotoId = { "cliente-1" }, io = Dispatchers.Unconfined)
        val billetera = BilleteraRepository(api = api, usuarioRemotoId = { "cliente-1" }, io = Dispatchers.Unconfined)
        return PagoQrViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Ruta.ARG_PEDIDO_REMOTO to pedido)),
            pagos = pagos,
            billetera = billetera,
            nuevoUuid = { UUID_OP },
            intervaloConsultaMs = 0L,
            maxConsultas = 3
        )
    }

    @Test
    fun alAbrirCargaPagoYSaldoSinGenerarQr() {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("PENDIENTE"), pedidoId = ApiPagosFake.PEDIDO)
        )
        val vm = modelo(api)
        val estado = vm.estado.value
        assertFalse(estado.cargando)
        assertNull(estado.qr)
        assertFalse(estado.mostrarQr)
        assertEquals("PENDIENTE", estado.pago?.estado)
        assertEquals(8_500L, estado.saldoDisponibleCentimos)
        assertEquals(0, api.generados)
        assertEquals(1, api.consultasPedido)
        assertEquals(0, api.confirmaciones)
    }

    @Test
    fun pagarAhoraConfirmaSinQrYRefrescaEstado() {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("PENDIENTE"), pedidoId = ApiPagosFake.PEDIDO)
        )
        val vm = modelo(api)
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("CONFIRMADO"), pedidoId = ApiPagosFake.PEDIDO)
        )
        vm.confirmar()
        val estado = vm.estado.value
        assertTrue(estado.confirmado)
        assertFalse(estado.confirmando)
        assertEquals("PAGADO", estado.pedidoEstado)
        assertEquals(8_500L, estado.saldoClienteCentimos)
        assertEquals("CONFIRMADO", estado.pago?.estado)
        assertEquals(1, api.confirmaciones)
        assertEquals(listOf(UUID_OP), api.uuidsConfirmacion)
        assertEquals(0, api.generados)
    }

    @Test
    fun verCodigoQrEsOpcionalYNoConfirma() {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("PENDIENTE"), pedidoId = ApiPagosFake.PEDIDO)
        )
        val vm = modelo(api)
        vm.mostrarQr()
        val estado = vm.estado.value
        assertTrue(estado.mostrarQr)
        assertNotNull(estado.qr)
        assertEquals(1, api.generados)
        assertFalse(estado.confirmado)
        assertEquals(0, api.confirmaciones)
    }

    @Test
    fun noDuplicaLaConfirmacionMientrasHayUnaEnCurso() {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("PENDIENTE"), pedidoId = ApiPagosFake.PEDIDO)
        )
        api.puertaConfirmar = CompletableDeferred()
        val vm = modelo(api)
        vm.confirmar()
        assertTrue(vm.estado.value.confirmando)
        vm.confirmar()
        vm.confirmar()
        assertEquals(1, api.confirmaciones)
        api.puertaConfirmar!!.complete(api.confirmacion)
        assertTrue(vm.estado.value.confirmado)
        assertEquals(1, api.confirmaciones)
    }

    @Test
    fun errorDelServidorNoMarcaElPagoComoConfirmado() {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("PENDIENTE"), pedidoId = ApiPagosFake.PEDIDO)
        )
        api.confirmacion = ApiPagosFake.errorHttp(
            409,
            """{"error":"Saldo insuficiente","codigo":"SALDO_INSUFICIENTE"}"""
        )
        val vm = modelo(api)
        vm.confirmar()
        val estado = vm.estado.value
        assertFalse(estado.confirmado)
        assertFalse(estado.confirmando)
        assertEquals("SALDO_INSUFICIENTE", estado.codigoError)
        assertEquals("Saldo insuficiente", estado.error)
        assertEquals("PENDIENTE", estado.pago?.estado)
    }

    @Test
    fun respuestaQueNoDeclaraConfirmadoNoCuentaComoPago() {
        val api = ApiPagosFake()
        api.pagoPedido = Response.success(
            PagoResponse(pago = ApiPagosFake.pagoDto("PENDIENTE"), pedidoId = ApiPagosFake.PEDIDO)
        )
        api.confirmacion = Response.success(
            com.megamarket.cliente.data.remote.ConfirmarPagoResponse(
                pago = ApiPagosFake.pagoDto("PENDIENTE"),
                pedido = PedidoEstadoPagoDto(ApiPagosFake.PEDIDO, "CONFIRMADO"),
                billetera = null
            )
        )
        val vm = modelo(api)
        vm.confirmar()
        assertFalse(vm.estado.value.confirmado)
        assertEquals("PAGO_NO_CONFIRMADO", vm.estado.value.codigoError)
    }

    companion object {
        private const val UUID_OP = "11111111-1111-4111-8111-111111111111"
    }
}
