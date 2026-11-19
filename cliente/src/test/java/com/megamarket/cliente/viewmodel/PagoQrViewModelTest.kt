package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.megamarket.cliente.data.remote.ConfirmarPagoResponse
import com.megamarket.cliente.data.remote.PagoResponse
import com.megamarket.cliente.data.remote.PedidoEstadoPagoDto
import com.megamarket.cliente.data.repository.ApiPagosFake
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
        val repo = PagosRepository(api = api, usuarioRemotoId = { "cliente-1" }, io = Dispatchers.Unconfined)
        return PagoQrViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Ruta.ARG_PEDIDO_REMOTO to pedido)),
            pagos = repo,
            nuevoUuid = { UUID_OP },
            intervaloConsultaMs = 0L,
            maxConsultas = 3
        )
    }

    @Test
    fun alAbrirGeneraElQrSinConfirmarElPago() {
        val api = ApiPagosFake()
        val vm = modelo(api)
        val estado = vm.estado.value
        assertFalse(estado.cargando)
        assertNotNull(estado.qr)
        assertEquals("SIMULACIÓN", estado.qr?.etiqueta)
        assertEquals("QR_GENERADO", estado.pago?.estado)
        assertFalse(estado.confirmado)
        assertEquals(1, api.generados)
        assertEquals(0, api.confirmaciones)
    }

    @Test
    fun confirmarMarcaPagadoSoloConLaRespuestaDelServidorYRefrescaEstado() {
        val api = ApiPagosFake()
        val vm = modelo(api)
        // Tras confirmar, el GET del pedido refleja el pago confirmado.
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
        assertEquals(1, api.consultasPedido)
    }

    @Test
    fun noDuplicaLaConfirmacionMientrasHayUnaEnCurso() {
        val api = ApiPagosFake()
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
        assertEquals("QR_GENERADO", estado.pago?.estado)
    }

    @Test
    fun respuestaQueNoDeclaraConfirmadoNoCuentaComoPago() {
        val api = ApiPagosFake()
        api.confirmacion = Response.success(
            ConfirmarPagoResponse(
                pago = ApiPagosFake.pagoDto("QR_GENERADO"),
                pedido = PedidoEstadoPagoDto(ApiPagosFake.PEDIDO, "CONFIRMADO")
            )
        )
        val vm = modelo(api)
        vm.confirmar()
        assertFalse(vm.estado.value.confirmado)
        assertEquals("PAGO_NO_CONFIRMADO", vm.estado.value.codigoError)
    }

    @Test
    fun errorDeRedConsultaElEstadoYReutilizaElMismoUuidAlReintentar() {
        val api = ApiPagosFake().apply { fallosRedConfirmacion = 1 }
        val vm = modelo(api)
        vm.confirmar()
        assertFalse(vm.estado.value.confirmado)
        assertNotNull(vm.estado.value.error)
        // El seguimiento consultó al servidor para saber si la confirmación llegó a aplicarse.
        assertTrue(api.consultasPedido >= 1)
        vm.confirmar()
        assertTrue(vm.estado.value.confirmado)
        assertEquals(listOf(UUID_OP, UUID_OP), api.uuidsConfirmacion)
    }

    @Test
    fun pagoYaConfirmadoNoMuestraQrNiVuelveAConfirmar() {
        val api = ApiPagosFake()
        api.qr = Response.success(
            com.megamarket.cliente.data.remote.GenerarQrResponse(
                pago = ApiPagosFake.pagoDto("CONFIRMADO").copy(qrContenido = null),
                yaConfirmado = true
            )
        )
        val vm = modelo(api)
        assertTrue(vm.estado.value.confirmado)
        assertNull(vm.estado.value.qr)
        vm.confirmar()
        assertEquals(0, api.confirmaciones)
    }

    @Test
    fun pedidoSinIdMuestraErrorYNoLlamaALaApi() {
        val api = ApiPagosFake()
        val vm = modelo(api, pedido = "")
        assertNotNull(vm.estado.value.error)
        assertEquals(0, api.generados)
    }

    private companion object {
        const val UUID_OP = "44444444-4444-4444-8444-444444444444"
    }
}
