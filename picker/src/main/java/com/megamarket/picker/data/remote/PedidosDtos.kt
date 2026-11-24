package com.megamarket.picker.data.remote

data class ErrorApiDto(
    val error: String?,
    val codigo: String?,
    val sustitucionId: String? = null
)

data class PedidosResponse(
    val pedidos: List<PedidoListaDto>?,
    val pagina: Int? = null,
    val limite: Int? = null,
    val total: Int? = null,
    val hayMas: Boolean? = null
)

data class PedidoListaDto(
    val id: String?,
    val clienteId: String?,
    val estado: String?,
    val totalCentimos: Long?,
    val franjaEntregaId: String?,
    val pickerId: String?,
    val fecha: String?,
    val version: Long?
)

data class PedidoCompletoResponse(
    val pedido: PedidoListaDto?,
    val pago: PagoDto?
)

data class PagoDto(
    val modalidad: String?,
    val estado: String?,
    val id: String? = null,
    val montoCentimos: Long? = null
)

data class TransicionPedidoRequest(
    val estado: String
)

data class PickingListaResponse(
    val pedidos: List<PickingItemDto>?,
    val pagina: Int? = null,
    val limite: Int? = null,
    val total: Int? = null,
    val hayMas: Boolean? = null
)

data class PickingItemDto(
    val pedido: PedidoPickingDto?,
    val detalles: List<LineaPickingDto>?
)

data class PickingDetalleResponse(
    val pedido: PedidoPickingDto?,
    val detalles: List<LineaPickingDto>?,
    val pago: PagoDto?
)

data class PedidoPickingDto(
    val id: String?,
    val clienteId: String?,
    val estado: String?,
    val totalCentimos: Long?,
    val franjaEntregaId: String?,
    val pickerId: String?,
    val preferenciaSustitucion: String?,
    val fecha: String?,
    val version: Long?,
    val updatedAt: String?
)

data class LineaPickingDto(
    val id: String?,
    val pedidoId: String?,
    val productoId: String?,
    val nombreProducto: String?,
    val cantidad: Int?,
    val cantidadArmada: Int?,
    val estadoPicking: String?,
    val precioUnitarioCentimos: Long?,
    val subtotalCentimos: Long?,
    val pasillo: String?,
    val codigoBarras: String?,
    val fechaEscaneo: String?,
    val escaneadoPor: String?,
    val sustitucionAceptadaId: String? = null,
    val productoSustitutoId: String? = null,
    val nombreSustituto: String? = null,
    val resolubleSinProducto: Boolean? = null
)

data class EscaneoRequest(
    val uuidOperacion: String,
    val codigoBarras: String,
    val cantidad: Int
)

data class EscaneoDto(
    val id: String? = null,
    val uuidOperacion: String? = null,
    val pedidoDetalleId: String? = null,
    val pickerId: String? = null,
    val codigoBarras: String? = null,
    val cantidad: Int? = null,
    val resultado: String? = null,
    val fechaEscaneo: String? = null
)

data class EscaneoResponse(
    val idempotente: Boolean? = null,
    val escaneo: EscaneoDto? = null,
    val detalle: LineaPickingDto? = null,
    val detalles: List<LineaPickingDto>? = null,
    val error: String? = null,
    val codigo: String? = null
)

data class FaltanteResponse(
    val idempotente: Boolean? = null,
    val detalle: LineaPickingDto? = null,
    val detalles: List<LineaPickingDto>? = null,
    val error: String? = null,
    val codigo: String? = null
)
