package com.megamarket.cliente.data.repository

import com.google.gson.Gson
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.ConfirmarPagoRequest
import com.megamarket.cliente.data.remote.ErrorApiDto
import com.megamarket.cliente.data.remote.GenerarQrRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Envoltorio delgado sobre los endpoints de pagos (`api/pagos`). El pago solo se considera confirmado cuando
 * la respuesta del servidor lo declara; nunca se decide localmente.
 */
class PagosRepository(
    private val api: ApiService,
    private val usuarioRemotoId: () -> String?,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun generarQr(pedidoId: String): ResultadoPagos<SesionPagoQr> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        val id = pedidoId.trim()
        if (id.isEmpty()) return@withContext pedidoNoDisponible()
        try {
            val respuesta = api.generarQrPago(GenerarQrRequest(id), owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            val cuerpo = respuesta.body()
            val pago = cuerpo?.pago?.aPagoCliente()
                ?: return@withContext respuestaInvalidaPago()
            ResultadoPagos.Exito(SesionPagoQr(pago, cuerpo.qr?.aQrCliente(pago)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    suspend fun obtenerPorPedido(pedidoId: String): ResultadoPagos<PagoCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        val id = pedidoId.trim()
        if (id.isEmpty()) return@withContext pedidoNoDisponible()
        try {
            val respuesta = api.obtenerPagoDelPedido(id, owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            respuesta.body()?.pago?.aPagoCliente()
                ?.let { ResultadoPagos.Exito(it) }
                ?: respuestaInvalidaPago()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    suspend fun obtenerPago(pagoId: String): ResultadoPagos<PagoCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        val id = pagoId.trim()
        if (id.isEmpty()) return@withContext noEncontradoPago()
        try {
            val respuesta = api.obtenerPago(id, owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            respuesta.body()?.pago?.aPagoCliente()
                ?.let { ResultadoPagos.Exito(it) }
                ?: respuestaInvalidaPago()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    /**
     * Confirma el pago simulado. [uuidOperacion] debe reutilizarse en los reintentos del mismo pago
     * para que el servidor responda de forma idempotente.
     */
    suspend fun confirmar(
        pagoId: String,
        uuidOperacion: String
    ): ResultadoPagos<ConfirmacionPagoCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        val id = pagoId.trim()
        if (id.isEmpty()) return@withContext noEncontradoPago()
        try {
            val respuesta = api.confirmarPago(id, ConfirmarPagoRequest(uuidOperacion), owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            val cuerpo = respuesta.body()
            val pago = cuerpo?.pago?.aPagoCliente()
                ?: return@withContext respuestaInvalidaPago()
            if (!pago.confirmado) {
                return@withContext ResultadoPagos.Error(
                    TipoErrorSustitucion.SERVIDOR,
                    MensajesPagoCliente.NO_CONFIRMADO,
                    "PAGO_NO_CONFIRMADO"
                )
            }
            ResultadoPagos.Exito(
                ConfirmacionPagoCliente(
                    pago = pago,
                    pedidoEstado = cuerpo.pedido?.estado?.trim()?.uppercase(),
                    saldoClienteCentimos = cuerpo.billetera?.saldoClienteCentimos,
                    idempotente = cuerpo.idempotente == true
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    /**
     * Cancela un pedido CONFIRMADO sin cobro confirmado (p. ej. ONLINE pendiente).
     * El servidor rechaza si el pago ya está CONFIRMADO.
     */
    suspend fun cancelarPedido(pedidoId: String): ResultadoPagos<Unit> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        val id = pedidoId.trim()
        if (id.isEmpty()) return@withContext pedidoNoDisponible()
        try {
            val respuesta = api.transicionarPedido(
                id,
                com.megamarket.cliente.data.remote.TransicionPedidoRequest(estado = "CANCELADO"),
                owner
            )
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            ResultadoPagos.Exito(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    suspend fun confirmarAjuste(
        ajusteId: String,
        uuidOperacion: String
    ): ResultadoPagos<ConfirmacionAjusteCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        val id = ajusteId.trim()
        if (id.isEmpty()) return@withContext noEncontradoPago()
        try {
            val respuesta = api.confirmarAjustePago(id, ConfirmarPagoRequest(uuidOperacion), owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            val cuerpo = respuesta.body() ?: return@withContext respuestaInvalidaPago()
            ResultadoPagos.Exito(
                ConfirmacionAjusteCliente(
                    ajusteId = cuerpo.ajuste?.id,
                    estado = cuerpo.ajuste?.estado?.trim()?.uppercase(),
                    saldoClienteCentimos = cuerpo.billetera?.saldoClienteCentimos,
                    idempotente = cuerpo.idempotente == true
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    private fun <T> pedidoNoDisponible(): ResultadoPagos<T> =
        ResultadoPagos.Error(
            TipoErrorSustitucion.NO_ENCONTRADO,
            MensajesPagoCliente.PEDIDO_NO_DISPONIBLE
        )

    private fun <T> noEncontradoPago(): ResultadoPagos<T> =
        ResultadoPagos.Error(TipoErrorSustitucion.NO_ENCONTRADO, MensajesPagoCliente.NO_ENCONTRADO)
}

private val gsonPagos = Gson()

internal fun <T> sesionVencidaPago(): ResultadoPagos<T> =
    ResultadoPagos.Error(TipoErrorSustitucion.SESION, MensajesPagoCliente.SESION)

internal fun <T> respuestaInvalidaPago(): ResultadoPagos<T> =
    ResultadoPagos.Error(TipoErrorSustitucion.SERVIDOR, MensajesPagoCliente.RESPUESTA_INVALIDA)

internal fun <T> falloRedPago(error: IOException): ResultadoPagos<T> {
    if (error.message == "La cuenta activa cambió") {
        return ResultadoPagos.Error(TipoErrorSustitucion.CUENTA, MensajesSustitucionCliente.CUENTA)
    }
    return ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
}

internal fun <T> falloPago(response: Response<*>): ResultadoPagos<T> {
    val crudo = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
    val dto = runCatching { gsonPagos.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()
    val tipo = when (response.code()) {
        401 -> TipoErrorSustitucion.SESION
        403 -> TipoErrorSustitucion.PROHIBIDO
        404 -> TipoErrorSustitucion.NO_ENCONTRADO
        409 -> TipoErrorSustitucion.CONFLICTO
        else -> TipoErrorSustitucion.SERVIDOR
    }
    val porDefecto = when (tipo) {
        TipoErrorSustitucion.SESION -> MensajesPagoCliente.SESION
        TipoErrorSustitucion.PROHIBIDO -> MensajesPagoCliente.PROHIBIDO
        TipoErrorSustitucion.NO_ENCONTRADO -> MensajesPagoCliente.NO_ENCONTRADO
        TipoErrorSustitucion.CONFLICTO -> MensajesPagoCliente.CONFLICTO
        else -> MensajesPagoCliente.SERVIDOR
    }
    return ResultadoPagos.Error(
        tipo,
        dto?.error?.takeIf { it.isNotBlank() } ?: porDefecto,
        dto?.codigo
    )
}
