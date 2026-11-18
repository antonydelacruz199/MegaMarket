package com.megamarket.picker.data.remote

data class CandidatosSustitucionResponse(
    val detalleId: String?,
    val precioOriginalCentimos: Long?,
    val tolerancia: Double?,
    val candidatos: List<CandidatoSustitutoDto>?
)

data class CandidatoSustitutoDto(
    val id: String?,
    val nombre: String?,
    val marca: String?,
    val categoriaId: String?,
    val pasillo: String?,
    val codigoBarras: String?,
    val precioCentimos: Long?,
    val stockDisponible: Int?,
    val dentroTolerancia: Boolean?
)

data class PropuestaSustitucionRequest(
    val productoSustitutoId: String,
    val cantidad: Int? = null
)

data class SustitucionResponse(
    val sustitucion: SustitucionDto?,
    val error: String? = null,
    val codigo: String? = null,
    val sustitucionId: String? = null
)

data class SustitucionDto(
    val id: String?,
    val pedidoDetalleId: String?,
    val pedidoId: String?,
    val productoOriginalId: String?,
    val productoSustitutoId: String?,
    val pickerId: String?,
    val cantidad: Int?,
    val precioOriginalCentimos: Long?,
    val precioSustitutoCentimos: Long?,
    val estado: String?,
    val fechaPropuesta: String?,
    val fechaLimite: String?,
    val fechaRespuesta: String?,
    val respuestaAutomatica: Boolean?,
    val createdAt: String?,
    val updatedAt: String?
)
