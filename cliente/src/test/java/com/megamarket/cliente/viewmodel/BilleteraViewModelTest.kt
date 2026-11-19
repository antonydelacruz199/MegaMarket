package com.megamarket.cliente.viewmodel

import com.megamarket.cliente.data.repository.ApiPagosFake
import com.megamarket.cliente.data.repository.BilleteraRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class BilleteraViewModelTest {

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun modelo(api: ApiPagosFake, owner: String? = "cliente-1") =
        BilleteraViewModel(
            BilleteraRepository(api = api, usuarioRemotoId = { owner }, io = Dispatchers.Unconfined)
        )

    @Test
    fun cargaSaldoYMovimientosAlIniciar() {
        val vm = modelo(ApiPagosFake())
        val estado = vm.estado.value
        assertFalse(estado.cargando)
        assertEquals(8_500L, estado.cuenta?.saldoCentimos)
        assertEquals(10_000L, estado.saldoInicialCentimos)
        assertEquals(1, estado.movimientos.size)
        assertNull(estado.error)
    }

    @Test
    fun errorDeServidorMuestraMensajeYPermiteReintentar() {
        val api = ApiPagosFake()
        api.saldo = ApiPagosFake.errorHttp(500, """{"error":"No se pudo consultar el saldo"}""")
        val vm = modelo(api)
        assertNull(vm.estado.value.cuenta)
        assertEquals("No se pudo consultar el saldo", vm.estado.value.error)

        api.saldo = ApiPagosFake().saldo
        vm.cargar()
        assertNotNull(vm.estado.value.cuenta)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun sinSesionMarcaSesionVencida() {
        val vm = modelo(ApiPagosFake(), owner = null)
        assertTrue(vm.estado.value.sesionVencida)
    }
}
