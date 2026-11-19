package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.BilleteraMovimientosResponse
import com.megamarket.cliente.data.remote.BilleteraSaldoResponse
import com.megamarket.cliente.data.remote.CategoriasResponse
import com.megamarket.cliente.data.remote.ConfirmarAjusteResponse
import com.megamarket.cliente.data.remote.ConfirmarPagoRequest
import com.megamarket.cliente.data.remote.ConfirmarPagoResponse
import com.megamarket.cliente.data.remote.CrearPedidoRequest
import com.megamarket.cliente.data.remote.CrearPedidoResponse
import com.megamarket.cliente.data.remote.CuentaBilleteraDto
import com.megamarket.cliente.data.remote.GenerarQrRequest
import com.megamarket.cliente.data.remote.GenerarQrResponse
import com.megamarket.cliente.data.remote.ListaPedidosResponse
import com.megamarket.cliente.data.remote.ListaSustitucionesResponse
import com.megamarket.cliente.data.remote.LoginRequest
import com.megamarket.cliente.data.remote.LoginResponse
import com.megamarket.cliente.data.remote.MovimientoBilleteraDto
import com.megamarket.cliente.data.remote.PagoPedidoRemotoDto
import com.megamarket.cliente.data.remote.PagoResponse
import com.megamarket.cliente.data.remote.PedidoCompletoResponse
import com.megamarket.cliente.data.remote.PedidoEstadoPagoDto
import com.megamarket.cliente.data.remote.ProductosResponse
import com.megamarket.cliente.data.remote.QrSimuladoDto
import com.megamarket.cliente.data.remote.ReservaRequest
import com.megamarket.cliente.data.remote.ReservaResponse
import com.megamarket.cliente.data.remote.ResumenBilleteraDto
import com.megamarket.cliente.data.remote.SustitucionDecisionResponse
import kotlinx.coroutines.CompletableDeferred
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException

/** Fake de [ApiService] solo con los endpoints de pagos y billetera (Fase 3). */
internal class ApiPagosFake : ApiService {
    var qr: Response<GenerarQrResponse> = Response.success(
        GenerarQrResponse(pago = pagoDto("QR_GENERADO"), qr = qrDto(), simulacion = true)
    )
    var pagoPedido: Response<PagoResponse> =
        Response.success(PagoResponse(pago = pagoDto("QR_GENERADO"), pedidoId = PEDIDO))
    var confirmacion: Response<ConfirmarPagoResponse> = Response.success(
        ConfirmarPagoResponse(
            pago = pagoDto("CONFIRMADO"),
            pedido = PedidoEstadoPagoDto(PEDIDO, "PAGADO"),
            billetera = ResumenBilleteraDto(saldoClienteCentimos = 8_500, saldoComercioCentimos = 1_500),
            simulacion = true
        )
    )
    var saldo: Response<BilleteraSaldoResponse> = Response.success(
        BilleteraSaldoResponse(
            cuenta = CuentaBilleteraDto("c1", "CLIENTE", 8_500, "PEN", true),
            saldoInicialCentimos = 10_000,
            simulacion = true
        )
    )
    var movimientos: Response<BilleteraMovimientosResponse> = Response.success(
        BilleteraMovimientosResponse(
            cuenta = CuentaBilleteraDto("c1", "CLIENTE", 8_500, "PEN", true),
            movimientos = listOf(
                MovimientoBilleteraDto(
                    id = "m1",
                    tipo = "COBRO_PEDIDO",
                    sentido = "DEBITO",
                    montoCentimos = 1_500,
                    saldoPosteriorCentimos = 8_500,
                    pagoId = PAGO,
                    pedidoId = PEDIDO,
                    descripcion = "Cobro del pedido",
                    createdAt = "2030-01-01T00:00:00.000Z",
                    simulacion = true
                )
            ),
            simulacion = true
        )
    )

    /** Número de confirmaciones que fallarán por red antes de responder. */
    var fallosRedConfirmacion = 0
    var puertaConfirmar: CompletableDeferred<Response<ConfirmarPagoResponse>>? = null

    var generados = 0
    var consultasPedido = 0
    var confirmaciones = 0
    val uuidsConfirmacion = mutableListOf<String>()

    override suspend fun generarQrPago(body: GenerarQrRequest, owner: String): Response<GenerarQrResponse> {
        generados++
        return qr
    }

    override suspend fun obtenerPagoDelPedido(pedidoId: String, owner: String): Response<PagoResponse> {
        consultasPedido++
        return pagoPedido
    }

    override suspend fun obtenerPago(id: String, owner: String): Response<PagoResponse> = pagoPedido

    override suspend fun confirmarPago(
        id: String,
        body: ConfirmarPagoRequest,
        owner: String
    ): Response<ConfirmarPagoResponse> {
        confirmaciones++
        uuidsConfirmacion += body.uuidOperacion
        if (fallosRedConfirmacion > 0) {
            fallosRedConfirmacion--
            throw IOException("sin red")
        }
        puertaConfirmar?.let { return it.await() }
        return confirmacion
    }

    override suspend fun confirmarAjustePago(
        id: String,
        body: ConfirmarPagoRequest,
        owner: String
    ): Response<ConfirmarAjusteResponse> = Response.success(ConfirmarAjusteResponse())

    override suspend fun obtenerSaldoBilletera(owner: String): Response<BilleteraSaldoResponse> = saldo

    override suspend fun listarMovimientosBilletera(owner: String): Response<BilleteraMovimientosResponse> =
        movimientos

    override suspend fun reservarCarrito(id: String, body: ReservaRequest, owner: String): Response<ReservaResponse> =
        error("n/a")
    override suspend fun login(body: LoginRequest): Response<LoginResponse> = error("n/a")
    override suspend fun listarCategorias(owner: String?): Response<CategoriasResponse> = error("n/a")
    override suspend fun listarProductos(owner: String?): Response<ProductosResponse> = error("n/a")
    override suspend fun listarFranjas(owner: String?) = error("n/a")
    override suspend fun obtenerFranja(id: String, owner: String?) = error("n/a")
    override suspend fun crearPedido(body: CrearPedidoRequest, owner: String?): Response<CrearPedidoResponse> =
        error("n/a")
    override suspend fun listarPedidos(estado: String?, owner: String): Response<ListaPedidosResponse> =
        error("n/a")
    override suspend fun obtenerPedido(id: String, owner: String): Response<PedidoCompletoResponse> =
        error("n/a")
    override suspend fun transicionarPedido(
        id: String,
        body: com.megamarket.cliente.data.remote.TransicionPedidoRequest,
        owner: String
    ): Response<PedidoCompletoResponse> = error("n/a")
    override suspend fun listarSustituciones(estado: String?, owner: String): Response<ListaSustitucionesResponse> =
        error("n/a")
    override suspend fun obtenerSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> =
        error("n/a")
    override suspend fun aceptarSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> =
        error("n/a")
    override suspend fun rechazarSustitucion(id: String, owner: String): Response<SustitucionDecisionResponse> =
        error("n/a")

    companion object {
        const val PEDIDO = "11111111-1111-4111-8111-111111111111"
        const val PAGO = "22222222-2222-4222-8222-222222222222"

        fun pagoDto(estado: String, modalidad: String = "ONLINE") = PagoPedidoRemotoDto(
            id = PAGO,
            uuidOperacion = "33333333-3333-4333-8333-333333333333",
            pedidoId = PEDIDO,
            montoCentimos = 1_500,
            moneda = "PEN",
            medioPago = "QR_SIMULADO",
            modalidad = modalidad,
            estado = estado,
            referenciaBanco = "SIM-REF-ABC",
            numeroOperacionBanco = if (estado == "CONFIRMADO") "SIM-OP-1" else null,
            qrContenido = "MEGAMARKET-SIM|$PAGO",
            fechaExpiracionQr = "2030-01-01T01:00:00.000Z",
            fechaConfirmacion = if (estado == "CONFIRMADO") "2030-01-01T00:30:00.000Z" else null,
            simulacion = true
        )

        fun qrDto() = QrSimuladoDto(
            contenido = "MEGAMARKET-SIM|$PAGO",
            referencia = "SIM-REF-ABC",
            importeCentimos = 1_500,
            pedidoId = PEDIDO,
            generadoEn = "2030-01-01T00:00:00.000Z",
            expiraEn = "2030-01-01T01:00:00.000Z",
            estado = "QR_GENERADO",
            etiqueta = "Código QR",
            aviso = "Ver el código no confirma el pago."
        )

        fun <T> errorHttp(codigo: Int, cuerpo: String): Response<T> =
            Response.error(codigo, cuerpo.toResponseBody("application/json".toMediaType()))
    }
}
