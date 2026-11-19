package com.megamarket.picker.data.remote

/** Contrato Fase 3 (pago QR simulado de contraentrega). Todo es académico, sin bancos reales. */

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

data class PagoSimuladoDto(
    val id: String? = null,
    val pedidoId: String? = null,
    val montoCentimos: Long? = null,
    val moneda: String? = null,
    val modalidad: String? = null,
    val estado: String? = null,
    val referenciaBanco: String? = null,
    val numeroOperacionBanco: String? = null,
    val qrContenido: String? = null,
    val fechaExpiracionQr: String? = null,
    val fechaConfirmacion: String? = null,
    val simulacion: Boolean? = null
)

data class GenerarQrResponse(
    val pago: PagoSimuladoDto? = null,
    val qr: QrSimuladoDto? = null,
    val idempotente: Boolean? = null,
    val yaConfirmado: Boolean? = null,
    val simulacion: Boolean? = null
)

data class PagoPedidoResponse(
    val pago: PagoSimuladoDto? = null,
    val pedidoId: String? = null
)

data class ConfirmarPagoRequest(val uuidOperacion: String)

data class PedidoEstadoPagoDto(
    val id: String? = null,
    val estado: String? = null
)

data class ConfirmarPagoResponse(
    val pago: PagoSimuladoDto? = null,
    val pedido: PedidoEstadoPagoDto? = null,
    val idempotente: Boolean? = null,
    val yaConfirmado: Boolean? = null,
    val simulacion: Boolean? = null,
    val aviso: String? = null
)
