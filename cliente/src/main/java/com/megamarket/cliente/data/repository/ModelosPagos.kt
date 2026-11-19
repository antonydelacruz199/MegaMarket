package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.CuentaBilleteraDto
import com.megamarket.cliente.data.remote.MovimientoBilleteraDto
import com.megamarket.cliente.data.remote.PagoPedidoRemotoDto
import com.megamarket.cliente.data.remote.QrSimuladoDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val MODALIDAD_ONLINE = "ONLINE"
const val MODALIDAD_CONTRAENTREGA = "CONTRAENTREGA"
const val ESTADO_PAGO_CONFIRMADO = "CONFIRMADO"
const val ETIQUETA_SIMULACION = "SIMULACIÓN"

data class PagoCliente(
    val id: String,
    val pedidoId: String?,
    val montoCentimos: Long,
    val moneda: String,
    val medioPago: String?,
    val modalidad: String?,
    val estado: String,
    val referenciaBanco: String?,
    val numeroOperacionBanco: String?,
    val qrContenido: String?,
    val fechaExpiracionQr: String?,
    val fechaConfirmacion: String?,
    val simulacion: Boolean
) {
    val confirmado: Boolean
        get() = estado.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true)
}

data class QrPagoCliente(
    val contenido: String,
    val referencia: String?,
    val importeCentimos: Long,
    val pedidoId: String?,
    val generadoEn: String?,
    val expiraEn: String?,
    val estado: String?,
    val etiqueta: String,
    val aviso: String?
)

/** Resultado de generar el QR: el QR puede faltar si el pago ya estaba confirmado. */
data class SesionPagoQr(
    val pago: PagoCliente,
    val qr: QrPagoCliente?
)

data class ConfirmacionPagoCliente(
    val pago: PagoCliente,
    val pedidoEstado: String?,
    val saldoClienteCentimos: Long?,
    val idempotente: Boolean
)

data class ConfirmacionAjusteCliente(
    val ajusteId: String?,
    val estado: String?,
    val saldoClienteCentimos: Long?,
    val idempotente: Boolean
)

data class CuentaBilleteraCliente(
    val id: String,
    val tipo: String,
    val saldoCentimos: Long,
    val moneda: String,
    val simulacion: Boolean
)

data class MovimientoBilleteraCliente(
    val id: String,
    val tipo: String,
    val sentido: String,
    val montoCentimos: Long,
    val saldoPosteriorCentimos: Long,
    val pagoId: String?,
    val pedidoId: String?,
    val descripcion: String?,
    val createdAt: String?
) {
    val esCredito: Boolean
        get() = sentido.equals("CREDITO", ignoreCase = true)

    /** Monto con signo para mostrar en lista (+ ingreso / − salida). */
    val montoConSigno: Long
        get() = if (esCredito) montoCentimos else -montoCentimos
}

data class BilleteraCliente(
    val cuenta: CuentaBilleteraCliente,
    val saldoInicialCentimos: Long?,
    val movimientos: List<MovimientoBilleteraCliente>
)

sealed class ResultadoPagos<out T> {
    data class Exito<T>(val valor: T) : ResultadoPagos<T>()
    data class Error(
        val tipo: TipoErrorSustitucion,
        val mensaje: String,
        val codigo: String? = null
    ) : ResultadoPagos<Nothing>()
}

object MensajesPagoCliente {
    const val SESION = "Tu sesión expiró. Vuelve a iniciar sesión."
    const val SIN_CONEXION = "Sin conexión. Revisa tu red e inténtalo de nuevo."
    const val SERVIDOR = "No se pudo completar la operación. Inténtalo más tarde."
    const val PROHIBIDO = "No tienes permiso para operar este pago."
    const val NO_ENCONTRADO = "No se encontró el pago del pedido."
    const val CONFLICTO = "El pago no admite esta operación en su estado actual."
    const val NO_CONFIRMADO = "El servidor no confirmó el pago. El pedido sigue pendiente de pago."
    const val RESPUESTA_INVALIDA = "El servidor devolvió una respuesta incompleta."
    const val PEDIDO_NO_DISPONIBLE = "El pedido todavía no se sincronizó con el servidor."
}

fun PagoPedidoRemotoDto.aPagoCliente(): PagoCliente? {
    val idPago = id?.trim().orEmpty()
    val estadoPago = estado?.trim().orEmpty()
    if (idPago.isEmpty() || estadoPago.isEmpty()) return null
    return PagoCliente(
        id = idPago,
        pedidoId = pedidoId?.trim()?.takeIf { it.isNotEmpty() },
        montoCentimos = montoCentimos ?: 0L,
        moneda = moneda?.trim().orEmpty().ifBlank { "PEN" },
        medioPago = medioPago,
        modalidad = modalidad?.trim()?.uppercase(),
        estado = estadoPago.uppercase(),
        referenciaBanco = referenciaBanco,
        numeroOperacionBanco = numeroOperacionBanco,
        qrContenido = qrContenido?.takeIf { it.isNotBlank() },
        fechaExpiracionQr = fechaExpiracionQr,
        fechaConfirmacion = fechaConfirmacion,
        simulacion = simulacion != false
    )
}

fun QrSimuladoDto.aQrCliente(pagoFallback: PagoCliente? = null): QrPagoCliente? {
    val texto = contenido?.takeIf { it.isNotBlank() } ?: pagoFallback?.qrContenido ?: return null
    return QrPagoCliente(
        contenido = texto,
        referencia = referencia ?: pagoFallback?.referenciaBanco,
        importeCentimos = importeCentimos ?: pagoFallback?.montoCentimos ?: 0L,
        pedidoId = pedidoId ?: pagoFallback?.pedidoId,
        generadoEn = generadoEn,
        expiraEn = expiraEn ?: pagoFallback?.fechaExpiracionQr,
        estado = estado,
        etiqueta = etiqueta?.takeIf { it.isNotBlank() } ?: ETIQUETA_SIMULACION,
        aviso = aviso
    )
}

fun CuentaBilleteraDto.aCuentaCliente(): CuentaBilleteraCliente? {
    val idCuenta = id?.trim().orEmpty()
    if (idCuenta.isEmpty()) return null
    return CuentaBilleteraCliente(
        id = idCuenta,
        tipo = tipo?.trim().orEmpty(),
        saldoCentimos = saldoCentimos ?: 0L,
        moneda = moneda?.trim().orEmpty().ifBlank { "PEN" },
        simulacion = simulacion != false
    )
}

fun MovimientoBilleteraDto.aMovimientoCliente(): MovimientoBilleteraCliente? {
    val idMov = id?.trim().orEmpty()
    if (idMov.isEmpty()) return null
    return MovimientoBilleteraCliente(
        id = idMov,
        tipo = tipo?.trim().orEmpty(),
        sentido = sentido?.trim().orEmpty(),
        montoCentimos = montoCentimos ?: 0L,
        saldoPosteriorCentimos = saldoPosteriorCentimos ?: 0L,
        pagoId = pagoId,
        pedidoId = pedidoId,
        descripcion = descripcion?.takeIf { it.isNotBlank() },
        createdAt = createdAt
    )
}

/** Fecha/hora legible en la zona del dispositivo; si no se puede interpretar devuelve el texto original. */
fun textoFechaHora(iso: String?): String {
    val crudo = iso?.trim().orEmpty()
    if (crudo.isEmpty()) return "—"
    val millis = parsearIsoAMillis(crudo) ?: return crudo.replace('T', ' ').take(16)
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}
