package com.megamarket.cliente.data.repository

import com.megamarket.modelo.SolicitudImagenProducto

object EstadoSustitucion {
    const val PENDIENTE = "PENDIENTE"
    const val ACEPTADA = "ACEPTADA"
    const val RECHAZADA = "RECHAZADA"
    const val CANCELADA = "CANCELADA"

    fun esPendiente(estado: String?): Boolean =
        estado?.trim().equals(PENDIENTE, ignoreCase = true) == true

    fun esDefinitivo(estado: String?): Boolean = when (estado?.trim()?.uppercase()) {
        ACEPTADA, RECHAZADA, CANCELADA -> true
        else -> false
    }
}

/**
 * Importe que paga el cliente por la línea sustituida.
 * Nunca supera el precio original (diferencia absorbida como DESCUENTO en servidor).
 */
fun importeClienteCentimos(
    precioOriginalCentimos: Long,
    precioSustitutoCentimos: Long,
    cantidad: Int
): Long {
    val unitario = minOf(precioOriginalCentimos, precioSustitutoCentimos).coerceAtLeast(0L)
    val cant = cantidad.coerceAtLeast(0)
    return unitario * cant
}

data class PropuestaSustitucionCliente(
    val id: String,
    val pedidoDetalleId: String,
    val pedidoId: String?,
    val pedidoLocalId: Long?,
    val productoOriginalId: String?,
    val productoSustitutoId: String?,
    val nombreOriginal: String,
    val nombreSustituto: String,
    val marcaOriginal: String?,
    val marcaSustituto: String?,
    val imagenOriginal: SolicitudImagenProducto?,
    val imagenSustituto: SolicitudImagenProducto?,
    val cantidad: Int,
    val precioOriginalCentimos: Long,
    val precioSustitutoCentimos: Long,
    val importeClienteCentimos: Long,
    val estado: String,
    val fechaPropuesta: String?,
    val fechaLimite: String?,
    val fechaRespuesta: String?,
    val respuestaAutomatica: Boolean
)

sealed class ResultadoSustitucion<out T> {
    data class Exito<T>(val valor: T) : ResultadoSustitucion<T>()
    data class Error(
        val tipo: TipoErrorSustitucion,
        val mensaje: String,
        val codigo: String? = null,
        val propuesta: PropuestaSustitucionCliente? = null
    ) : ResultadoSustitucion<Nothing>()
}

enum class TipoErrorSustitucion {
    SESION,
    PROHIBIDO,
    NO_ENCONTRADO,
    CONFLICTO,
    RED,
    SERVIDOR,
    CUENTA
}

object MensajesSustitucionCliente {
    const val SESION = "Tu sesión expiró. Vuelve a iniciar sesión."
    const val PROHIBIDO = "No tienes permiso para gestionar esta sustitución."
    const val NO_ENCONTRADA = "No se encontró la propuesta de sustitución."
    const val CONFLICTO = "La propuesta ya fue resuelta o no admite esta decisión."
    const val SIN_CONEXION = "Sin conexión. Revisa tu red e inténtalo de nuevo."
    const val SERVIDOR = "No se pudo completar la operación. Inténtalo más tarde."
    const val DECISION_EN_CURSO = "Ya hay una decisión en curso."
    const val NO_PENDIENTE = "Esta propuesta ya no admite aceptación ni rechazo."
    const val CUENTA = "La cuenta activa cambió."
}
