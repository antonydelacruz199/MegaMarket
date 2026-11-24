package com.megamarket.app.data.remote

/** Contrato Fase 3: billetera simulada del comercio. Todo es académico, sin bancos reales. */

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
    val simulacion: Boolean? = null
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
    val hayMas: Boolean? = null
)
