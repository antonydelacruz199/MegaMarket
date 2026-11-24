package com.megamarket.app.data.repository

import com.megamarket.app.data.remote.ActualizarProductoRequest
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.BilleteraMovimientosResponse
import com.megamarket.app.data.remote.BilleteraSaldoResponse
import com.megamarket.app.data.remote.CategoriasResponse
import com.megamarket.app.data.remote.CrearProductoRequest
import com.megamarket.app.data.remote.CuentaBilleteraDto
import com.megamarket.app.data.remote.EliminarProductoRequest
import com.megamarket.app.data.remote.EliminarProductoResponse
import com.megamarket.app.data.remote.LoginRequest
import com.megamarket.app.data.remote.LoginResponse
import com.megamarket.app.data.remote.MovimientoBilleteraDto
import com.megamarket.app.data.remote.MovimientoInventarioRequest
import com.megamarket.app.data.remote.MovimientoInventarioResponse
import com.megamarket.app.data.remote.ProductoResponse
import com.megamarket.app.data.remote.ProductosResponse
import com.megamarket.app.data.remote.SubidaImagenProductoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class BilleteraComercioRepositoryTest {

    @Test
    fun cargaSaldoYMovimientosDelServidor() = runBlocking {
        val api = ApiBilletera(
            saldo = Response.success(
                BilleteraSaldoResponse(
                    cuenta = CuentaBilleteraDto(id = "c1", tipo = "COMERCIO", saldoCentimos = 25_000, moneda = "PEN"),
                    saldoInicialCentimos = 0
                )
            ),
            movimientos = Response.success(
                BilleteraMovimientosResponse(
                    movimientos = listOf(
                        MovimientoBilleteraDto(
                            id = "m1", tipo = "PAGO", sentido = "CREDITO", montoCentimos = 15_000,
                            saldoPosteriorCentimos = 25_000, pedidoId = "p1", createdAt = "2030-01-01T10:00:00Z"
                        ),
                        MovimientoBilleteraDto(id = "m2", tipo = "AJUSTE", sentido = "DEBITO", montoCentimos = 500),
                        MovimientoBilleteraDto(id = null, montoCentimos = 1)
                    )
                )
            )
        )

        val resultado = BilleteraComercioRepository(api, io = Dispatchers.Unconfined).cargar()

        val billetera = (resultado as ResultadoBilleteraComercio.Ok).billetera
        assertEquals(25_000L, billetera.saldoCentimos)
        assertEquals("PEN", billetera.moneda)
        assertEquals(0L, billetera.saldoInicialCentimos)
        // El movimiento sin id se descarta.
        assertEquals(listOf("m1", "m2"), billetera.movimientos.map { it.id })
        assertTrue(billetera.movimientos[0].esCredito)
        assertEquals(15_000L, billetera.movimientos[0].montoConSigno)
        assertFalse(billetera.movimientos[1].esCredito)
        assertEquals(-500L, billetera.movimientos[1].montoConSigno)
    }

    @Test
    fun errorDelServidorSeMuestraSinInventarSaldo() = runBlocking {
        val api = ApiBilletera(
            saldo = Response.error(
                403,
                "{\"error\":\"Solo comercio\"}".toResponseBody("application/json".toMediaType())
            ),
            movimientos = Response.success(BilleteraMovimientosResponse())
        )

        val resultado = BilleteraComercioRepository(api, io = Dispatchers.Unconfined).cargar()

        assertEquals("Solo comercio", (resultado as ResultadoBilleteraComercio.Error).mensaje)
        assertEquals(0, api.llamadasMovimientos)
    }

    @Test
    fun respuestaSinSaldoEsInvalida() = runBlocking {
        val api = ApiBilletera(
            saldo = Response.success(BilleteraSaldoResponse()),
            movimientos = Response.success(BilleteraMovimientosResponse())
        )

        val resultado = BilleteraComercioRepository(api, io = Dispatchers.Unconfined).cargar()

        assertTrue(resultado is ResultadoBilleteraComercio.Error)
    }

    @Test
    fun sinRedDevuelveError() = runBlocking {
        val api = ApiBilletera(
            saldo = Response.success(BilleteraSaldoResponse()),
            movimientos = Response.success(BilleteraMovimientosResponse())
        ).apply { fallaRed = true }

        val resultado = BilleteraComercioRepository(api, io = Dispatchers.Unconfined).cargar()

        assertTrue(resultado is ResultadoBilleteraComercio.Error)
    }

    private class ApiBilletera(
        private val saldo: Response<BilleteraSaldoResponse>,
        private val movimientos: Response<BilleteraMovimientosResponse>
    ) : ApiService {
        var fallaRed = false
        var llamadasMovimientos = 0

        override suspend fun obtenerBilleteraComercio(): Response<BilleteraSaldoResponse> {
            if (fallaRed) throw IOException("sin red")
            return saldo
        }

        override suspend fun listarMovimientosBilletera(
            page: Int?,
            limit: Int?
        ): Response<BilleteraMovimientosResponse> {
            llamadasMovimientos++
            return movimientos
        }

        override suspend fun login(body: LoginRequest): Response<LoginResponse> = throw UnsupportedOperationException()
        override suspend fun listarCategorias(): Response<CategoriasResponse> = throw UnsupportedOperationException()
        override suspend fun listarProductos(
            page: Int?,
            limit: Int?,
            q: String?
        ): Response<ProductosResponse> = throw UnsupportedOperationException()
        override suspend fun obtenerProducto(id: String): Response<ProductoResponse> =
            throw UnsupportedOperationException()
        override suspend fun crearProducto(body: CrearProductoRequest): Response<ProductoResponse> =
            throw UnsupportedOperationException()
        override suspend fun actualizarProducto(
            id: String,
            body: ActualizarProductoRequest
        ): Response<ProductoResponse> = throw UnsupportedOperationException()
        override suspend fun eliminarProducto(
            id: String,
            body: EliminarProductoRequest
        ): Response<EliminarProductoResponse> = throw UnsupportedOperationException()
        override suspend fun registrarMovimiento(
            body: MovimientoInventarioRequest
        ): Response<MovimientoInventarioResponse> = throw UnsupportedOperationException()
        override suspend fun subirImagenProducto(
            productoId: String,
            archivo: MultipartBody.Part
        ): Response<SubidaImagenProductoResponse> = throw UnsupportedOperationException()
    }
}
