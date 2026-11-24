package com.megamarket.picker.data.repository

object EstadoSustitucion {
    const val PENDIENTE = "PENDIENTE"
    const val ACEPTADA = "ACEPTADA"
    const val RECHAZADA = "RECHAZADA"
    const val CANCELADA = "CANCELADA"
    const val SIN_STOCK = "SIN_STOCK"

    fun esPendiente(estado: String?): Boolean =
        estado?.trim().equals(PENDIENTE, ignoreCase = true) == true

    fun esRechazada(estado: String?): Boolean =
        estado?.trim().equals(RECHAZADA, ignoreCase = true) == true

    fun esSinStock(estado: String?): Boolean =
        estado?.trim().equals(SIN_STOCK, ignoreCase = true) == true

    fun estaResuelta(estado: String?): Boolean = when (estado?.trim()?.uppercase()) {
        ACEPTADA, RECHAZADA, CANCELADA, SIN_STOCK -> true
        else -> false
    }
}

data class CandidatoSustituto(
    val id: String,
    val nombre: String,
    val marca: String?,
    val cantidad: Int,
    val precioOriginalCentimos: Long,
    val precioSustitutoCentimos: Long,
    val pasillo: String?,
    val codigoBarras: String?,
    val stockDisponible: Int,
    val imagenUrl: String?
)

data class ListaCandidatosSustitucion(
    val detalleId: String,
    val precioOriginalCentimos: Long,
    val cantidadLinea: Int,
    val candidatos: List<CandidatoSustituto>
)

data class SustitucionPropuesta(
    val id: String,
    val pedidoDetalleId: String,
    val pedidoId: String?,
    val productoOriginalId: String?,
    val productoSustitutoId: String?,
    val cantidad: Int,
    val precioOriginalCentimos: Long,
    val precioSustitutoCentimos: Long,
    val estado: String,
    val fechaPropuesta: String?,
    val fechaLimite: String?,
    val fechaRespuesta: String?,
    val respuestaAutomatica: Boolean
)

object MensajesSustitucion {
    const val SOLO_FALTANTE = "Solo se proponen sustitutos para líneas confirmadas como faltante."
    const val FALTANTE_PENDIENTE =
        "El faltante aún no está confirmado por el servidor. Espera la sincronización."
    const val SIN_CANDIDATOS = "No hay productos en almacén para sustituir."
    const val PROPUESTA_EN_CURSO = "Ya hay una operación de sustitución en curso."
    const val SELECCIONA = "Selecciona un producto sustituto."
    const val PROHIBIDO = "No tienes permiso para proponer o consultar esta sustitución."
    const val NO_ENCONTRADA = "No se encontró la línea o la sustitución solicitada."
    const val CONFLICTO = "No se pudo registrar la propuesta por un conflicto en el pedido."
    const val SIN_CONEXION = MensajesSesion.SIN_CONEXION
    const val SESION = MensajesSesion.EXPIRADA
}
