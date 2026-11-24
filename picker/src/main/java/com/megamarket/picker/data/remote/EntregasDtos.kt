package com.megamarket.picker.data.remote

data class EntregasListaResponse(
    val pedidos: List<EntregaListaItemDto>?,
    val pagina: Int? = null,
    val limite: Int? = null,
    val total: Int? = null,
    val hayMas: Boolean? = null
)

data class EntregaListaItemDto(
    val pedido: PedidoEntregaDto?,
    val entrega: EntregaDto?
)

data class PedidoEntregaDto(
    val id: String?,
    val clienteId: String?,
    val estado: String?,
    val totalCentimos: Long?,
    val franjaEntregaId: String?,
    val repartidorId: String?,
    val fecha: String?,
    val version: Long?,
    val updatedAt: String?
)

data class EntregaDto(
    val id: String?,
    val uuidOperacion: String?,
    val pedidoId: String?,
    val repartidorId: String?,
    val fotoReferencia: String?,
    val latitud: Double?,
    val longitud: Double?,
    val fechaEntrega: String?,
    val createdAt: String?
)

data class DireccionEntregaDto(
    val departamento: String?,
    val provincia: String?,
    val distrito: String?,
    val direccion: String?,
    val telefono: String?
)

data class EntregaDetalleResponse(
    val pedido: PedidoEntregaDto?,
    val direccion: DireccionEntregaDto?,
    val entrega: EntregaDto?,
    val pago: PagoDto?
)

data class RegistrarEntregaRequest(
    val uuidOperacion: String,
    val fotoReferencia: String,
    val latitud: Double,
    val longitud: Double,
    val fechaEntrega: String?
)

data class RegistrarEntregaResponse(
    val idempotente: Boolean?,
    val entrega: EntregaDto?,
    val pedido: PedidoEntregaDto?,
    val compatibilidadHistorica: Boolean?
)

data class FotoEntregaResponse(
    val fotoReferencia: String?,
    val pedidoId: String?,
    val uso: String?
)
