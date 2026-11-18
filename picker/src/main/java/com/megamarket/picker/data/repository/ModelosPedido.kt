package com.megamarket.picker.data.repository

data class PedidoDisponible(
    val id: String,
    val estado: String,
    val totalCentimos: Long,
    val fecha: String?,
    val modalidad: String?
)

data class PedidoAsignado(
    val id: String,
    val totalCentimos: Long,
    val fecha: String?,
    val cantidadLineas: Int
)

data class LineaPreparacion(
    val id: String,
    val productoId: String,
    val nombre: String,
    val cantidad: Int,
    val cantidadArmada: Int,
    val estadoPicking: String,
    val pasillo: String?,
    val codigoBarras: String? = null,
    val imagenUrl: String?,
    val cantidadPendienteLocal: Int = 0,
    val faltantePendiente: Boolean = false,
    val operacionesLocales: List<ResumenOperacionLocal> = emptyList(),
    val sustitucionAceptadaId: String? = null,
    val productoSustitutoId: String? = null,
    val nombreSustituto: String? = null,
    val resolubleSinProducto: Boolean = false
) {
    /** Sustituto autorizado por el cliente; falta escanear físicamente. */
    fun requiereEscaneoSustituto(): Boolean {
        if (sustitucionAceptadaId.isNullOrBlank()) return false
        val estado = estadoPicking.uppercase()
        return estado == "FALTANTE" || estado == "PENDIENTE"
    }

    fun nombreVigente(): String =
        if (requiereEscaneoSustituto() || estadoPicking.equals("SUSTITUIDO", true)) {
            nombreSustituto?.takeIf { it.isNotBlank() } ?: nombre
        } else {
            nombre
        }

    fun productoImagenId(): String =
        if (!productoSustitutoId.isNullOrBlank() &&
            (requiereEscaneoSustituto() || estadoPicking.equals("SUSTITUIDO", true))
        ) {
            productoSustitutoId
        } else {
            productoId
        }
}

data class GrupoPasillo(
    val pasillo: String,
    val lineas: List<LineaPreparacion>
)

data class PreparacionPedido(
    val id: String,
    val totalCentimos: Long,
    val fecha: String?,
    val modalidadPago: String?,
    val estadoPago: String?,
    val grupos: List<GrupoPasillo>
)

private val ESTADOS_LINEA_LISTOS = setOf("ARMADO", "SUSTITUIDO", "ELIMINADO")

/** True si todas las líneas están resueltas y no hay sync pendiente (listo para delivery). */
fun PreparacionPedido.listoParaSalida(): Boolean {
    val lineas = grupos.flatMap { it.lineas }
    if (lineas.isEmpty()) return false
    return lineas.all { linea ->
        val estado = linea.estadoPicking.uppercase()
        estado in ESTADOS_LINEA_LISTOS &&
            linea.cantidadPendienteLocal == 0 &&
            !linea.faltantePendiente &&
            !linea.requiereEscaneoSustituto() &&
            (estado == "ELIMINADO" || linea.cantidadArmada >= linea.cantidad)
    }
}

/** Resumen legible para confirmar el cierre a LISTO. */
fun PreparacionPedido.resumenCierre(): String {
    val lineas = grupos.flatMap { it.lineas }
    val armados = lineas.count { it.estadoPicking.equals("ARMADO", true) }
    val sustituidos = lineas.count { it.estadoPicking.equals("SUSTITUIDO", true) }
    val eliminados = lineas.count { it.estadoPicking.equals("ELIMINADO", true) }
    return "Líneas: ${lineas.size} · Armadas: $armados · Sustituidas: $sustituidos · Eliminadas: $eliminados"
}

data class TableroPicker(
    val disponibles: List<PedidoDisponible>,
    val asignados: List<PedidoAsignado>
)

enum class TipoErrorPedido {
    RED,
    SESION,
    CUENTA,
    PROHIBIDO,
    CONFLICTO,
    NO_ENCONTRADO,
    SERVIDOR
}

sealed class ResultadoOperacion<out T> {
    data class Exito<T>(val valor: T) : ResultadoOperacion<T>()
    data class Error(
        val tipo: TipoErrorPedido,
        val mensaje: String,
        val codigo: String? = null,
        val fotoPublicada: Boolean = false
    ) : ResultadoOperacion<Nothing>()
}
