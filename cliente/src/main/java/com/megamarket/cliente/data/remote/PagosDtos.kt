package com.megamarket.cliente.data.remote

/** Contrato Fase 3: pagos QR simulados y billetera simulada. Todo es académico (sin bancos reales). */

data class GenerarQrRequest(val pedidoId: String)

data class QrSimuladoDto(
    val contenido: String? = null,
    val referencia: String? = null,
    val importeCentimos: Long? = null,
    val pedidoId: String? = null,
    val generadoEn: String? = null,
    val expiraEn: String? = null,
    val estado: String? = null,
    val etiqueta: String? = null,
    val aviso: String? = null
)

data class GenerarQrResponse(
    val pago: PagoPedidoRemotoDto? = null,
    val qr: QrSimuladoDto? = null,
    val idempotente: Boolean? = null,
    val yaConfirmado: Boolean? = null,
    val simulacion: Boolean? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class PagoResponse(
    val pago: PagoPedidoRemotoDto? = null,
    val pedidoId: String? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class ConfirmarPagoRequest(
    val uuidOperacion: String,
    val formaCobro: String? = null,
    val autorizacionClienteUuid: String? = null
)

data class AutorizarBilleteraResponse(
    val autorizacionUuid: String? = null,
    val pagoId: String? = null,
    val pedidoId: String? = null,
    val montoCentimos: Long? = null,
    val vigencia: String? = null,
    val simulacion: Boolean? = null,
    val aviso: String? = null,
    val idempotente: Boolean? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class PedidoEstadoPagoDto(
    val id: String? = null,
    val estado: String? = null
)

data class ResumenBilleteraDto(
    val saldoClienteCentimos: Long? = null,
    val saldoComercioCentimos: Long? = null
)

data class ConfirmarPagoResponse(
    val pago: PagoPedidoRemotoDto? = null,
    val pedido: PedidoEstadoPagoDto? = null,
    val billetera: ResumenBilleteraDto? = null,
    val movimientos: List<MovimientoBilleteraDto>? = null,
    val idempotente: Boolean? = null,
    val yaConfirmado: Boolean? = null,
    val simulacion: Boolean? = null,
    val aviso: String? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class ConfirmarAjusteResponse(
    val ajuste: AjustePagoDto? = null,
    val pago: PagoPedidoRemotoDto? = null,
    val billetera: ResumenBilleteraDto? = null,
    val movimientos: List<MovimientoBilleteraDto>? = null,
    val idempotente: Boolean? = null,
    val yaConfirmado: Boolean? = null,
    val simulacion: Boolean? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class CuentaBilleteraDto(
    val id: String? = null,
    val tipo: String? = null,
    val saldoCentimos: Long? = null,
    val moneda: String? = null,
    val simulacion: Boolean? = null
)

data class BilleteraSaldoResponse(
    val cuenta: CuentaBilleteraDto? = null,
    val saldoInicialCentimos: Long? = null,
    val simulacion: Boolean? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class MovimientoBilleteraDto(
    val id: String? = null,
    val uuidOperacion: String? = null,
    val tipo: String? = null,
    val sentido: String? = null,
    val montoCentimos: Long? = null,
    val saldoPosteriorCentimos: Long? = null,
    val pagoId: String? = null,
    val pedidoId: String? = null,
    val descripcion: String? = null,
    val createdAt: String? = null,
    val simulacion: Boolean? = null
)

data class BilleteraMovimientosResponse(
    val cuenta: CuentaBilleteraDto? = null,
    val movimientos: List<MovimientoBilleteraDto>? = null,
    val simulacion: Boolean? = null,
    val pagina: Int? = null,
    val limite: Int? = null,
    val total: Int? = null,
    val hayMas: Boolean? = null,
    val error: String? = null,
    val codigo: String? = null
)
