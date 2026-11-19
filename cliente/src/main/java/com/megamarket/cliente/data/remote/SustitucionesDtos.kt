package com.megamarket.cliente.data.remote

data class ListaSustitucionesResponse(
    val sustituciones: List<SustitucionDto>? = null,
    val error: String? = null
)

data class SustitucionDecisionResponse(
    val sustitucion: SustitucionDto? = null,
    val ajuste: AjustePagoDto? = null,
    val idempotente: Boolean? = null,
    val automatica: Boolean? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class AjustePagoDto(
    val id: String? = null,
    val tipo: String? = null,
    val motivo: String? = null,
    val montoCentimos: Long? = null,
    val estado: String? = null
)

data class SustitucionDto(
    val id: String? = null,
    val pedidoDetalleId: String? = null,
    val pedidoId: String? = null,
    val productoOriginalId: String? = null,
    val productoSustitutoId: String? = null,
    val pickerId: String? = null,
    val cantidad: Int? = null,
    val precioOriginalCentimos: Long? = null,
    val precioSustitutoCentimos: Long? = null,
    val estado: String? = null,
    val fechaPropuesta: String? = null,
    val fechaLimite: String? = null,
    val fechaRespuesta: String? = null,
    val respuestaAutomatica: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ListaPedidosResponse(
    val pedidos: List<PedidoRemotoDto>? = null,
    val error: String? = null
)

data class PedidoCompletoResponse(
    val pedido: PedidoRemotoDto? = null,
    val detalles: List<DetallePedidoRemotoDto>? = null,
    val direccion: DireccionPedidoRemotoDto? = null,
    val pago: PagoPedidoRemotoDto? = null,
    val error: String? = null
)

data class PedidoRemotoDto(
    val id: String? = null,
    val clienteId: String? = null,
    val clientUuid: String? = null,
    val fecha: String? = null,
    val totalCentimos: Long? = null,
    val estado: String? = null,
    val franjaEntregaId: String? = null,
    val pickerId: String? = null,
    val repartidorId: String? = null,
    val version: Long? = null,
    val preferenciaSustitucion: String? = null,
    val updatedAt: String? = null
)

data class DetallePedidoRemotoDto(
    val id: String? = null,
    val pedidoId: String? = null,
    val productoId: String? = null,
    val nombreProducto: String? = null,
    val precioUnitarioCentimos: Long? = null,
    val cantidad: Int? = null,
    val subtotalCentimos: Long? = null,
    val estadoPicking: String? = null,
    val cantidadArmada: Int? = null
)

data class DireccionPedidoRemotoDto(
    val id: String? = null,
    val pedidoId: String? = null,
    val departamento: String? = null,
    val provincia: String? = null,
    val distrito: String? = null,
    val direccion: String? = null,
    val telefono: String? = null
)

data class PagoPedidoRemotoDto(
    val id: String? = null,
    val uuidOperacion: String? = null,
    val pedidoId: String? = null,
    val montoCentimos: Long? = null,
    val moneda: String? = null,
    val medioPago: String? = null,
    val modalidad: String? = null,
    val estado: String? = null,
    val referenciaBanco: String? = null,
    val numeroOperacionBanco: String? = null,
    val qrContenido: String? = null,
    val fechaExpiracionQr: String? = null,
    val fechaConfirmacion: String? = null,
    val simulacion: Boolean? = null
)
