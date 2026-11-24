package com.megamarket.picker.data.repository

data class PedidoReparto(
    val id: String,
    val estado: String,
    val totalCentimos: Long,
    val fecha: String?,
    val repartidorId: String?
)

data class TableroReparto(
    val disponibles: List<PedidoReparto>,
    val enCamino: List<PedidoReparto>,
    val entregados: List<PedidoReparto>
)

data class PaginaReparto(
    val pedidos: List<PedidoReparto>,
    val pagina: Int,
    val hayMas: Boolean,
    val total: Int
)

data class DireccionReparto(
    val departamento: String,
    val provincia: String,
    val distrito: String,
    val direccion: String,
    val telefono: String
) {
    fun texto(): String = listOf(direccion, distrito, provincia, departamento)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
}

data class PagoReparto(
    val id: String? = null,
    val modalidad: String,
    val estado: String,
    val montoCentimos: Long? = null
) {
    val esContraentrega: Boolean
        get() = modalidad.equals(MODALIDAD_CONTRAENTREGA, ignoreCase = true)
}

const val MODALIDAD_CONTRAENTREGA = "CONTRAENTREGA"
const val ESTADO_PAGO_CONFIRMADO = "CONFIRMADO"
const val ETIQUETA_QR = "Código QR"

/** QR informativo de contraentrega. Mostrarlo no confirma el pago. */
data class PagoQrReparto(
    val pagoId: String,
    val estadoPago: String,
    val montoCentimos: Long,
    val referencia: String?,
    val qrContenido: String?,
    val generadoEn: String?,
    val expiraEn: String?,
    val etiqueta: String,
    val aviso: String?
) {
    val confirmado: Boolean
        get() = estadoPago.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true)
}

data class ConfirmacionPagoReparto(
    val estadoPago: String,
    val numeroOperacion: String?,
    val estadoPedido: String?,
    val idempotente: Boolean
)

data class EvidenciaReparto(
    val id: String,
    val uuidOperacion: String,
    val fotoReferencia: String,
    val latitud: Double,
    val longitud: Double,
    val fechaEntrega: String?
)

data class DetalleReparto(
    val pedido: PedidoReparto,
    val direccion: DireccionReparto?,
    val pago: PagoReparto?,
    val entrega: EvidenciaReparto?,
    val puedeFinalizar: Boolean
)

data class ResultadoRegistro(
    val estadoPedido: String,
    val idempotente: Boolean,
    val fotoReferencia: String,
    val uuidOperacion: String
)

data class Coordenadas(
    val latitud: Double,
    val longitud: Double
)

/** Tope por defecto de `MEDIA_MAX_BYTES` en la API (5 MiB). */
const val LIMITE_FOTO_BYTES = 5 * 1024 * 1024

const val CODIGO_REQUIERE_PAGO = "ENTREGA_REQUIERE_PAGO"
const val FORMA_COBRO_EFECTIVO = "EFECTIVO"
const val FORMA_COBRO_BILLETERA = "BILLETERA"

object MensajesEntrega {
    const val SOLO_REPARTIDOR = "Solo un repartidor puede consultar y registrar entregas."
    const val FOTO = "La fotografía de la entrega es obligatoria."
    const val FOTO_COMPRESION = "No se pudo preparar la fotografía dentro del límite de la API."
    const val GPS = "La ubicación GPS no es válida."
    const val PAGO =
        "ENTREGA_REQUIERE_PAGO. El pago debe estar confirmado antes de registrar la entrega."
    const val SOLO_EN_CAMINO = "Solo se registra la entrega de un pedido EN_CAMINO."
    const val NO_CONFIRMADO =
        "El servidor no confirmó el estado ENTREGADO. El pedido no se marcó como entregado."
    const val CAMINO_NO_CONFIRMADO = "El servidor no confirmó el paso a EN_CAMINO."
    const val CLAVE = "El servidor no devolvió una referencia de fotografía válida."
    const val PAGO_NO_CONFIRMADO =
        "El servidor no confirmó el pago. La entrega sigue bloqueada."
    const val PAGO_SOLO_CONTRAENTREGA = "Solo se cobra con QR cuando el pago es contraentrega."
    const val PAGO_RESPUESTA_INVALIDA = "El servidor devolvió un pago incompleto."
    const val ESTADO_LOGISTICO_ALTERADO =
        "El cobro alteró el estado logístico del pedido. No se puede continuar la entrega."
    const val PICKING_NO_CONFIRMADO = "El servidor no confirmó el paso a EN_PICKING."
}

private val UUID_ENTREGA = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)

private val CLAVE_ENTREGA = Regex(
    "^entregas/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\.(jpe?g|png|webp)$"
)

fun esUuidOperacion(valor: String?): Boolean = UUID_ENTREGA.matches(valor?.trim().orEmpty())

fun gpsValido(latitud: Double, longitud: Double): Boolean =
    latitud.isFinite() && longitud.isFinite() &&
        latitud in -90.0..90.0 && longitud in -180.0..180.0

/** Pago ausente lo resuelve el servidor (legado). Cualquier estado distinto de CONFIRMADO bloquea. */
fun pagoEstaPendiente(estado: String?): Boolean {
    val texto = estado?.trim().orEmpty()
    if (texto.isEmpty()) return false
    return !texto.equals("CONFIRMADO", ignoreCase = true)
}

fun puedeRegistrarEntrega(estadoPedido: String, estadoPago: String?, yaEntregado: Boolean): Boolean {
    if (yaEntregado) return false
    if (!estadoPedido.equals("EN_CAMINO", ignoreCase = true)) return false
    if (pagoEstaPendiente(estadoPago)) return false
    return true
}

fun fotoReferenciaDelPedido(clave: String?, pedidoId: String): Boolean {
    val texto = clave?.trim().orEmpty()
    if (pedidoId.isBlank() || texto.contains("..")) return false
    if (!texto.startsWith("entregas/$pedidoId/")) return false
    return CLAVE_ENTREGA.matches(texto)
}

/** Lee el tope que la API anuncia en el 413 (`máximo de N bytes`). */
fun limiteBytesAnunciado(mensaje: String?): Int? {
    if (mensaje.isNullOrBlank()) return null
    val match = Regex("""(\d{4,9})\s*bytes""", RegexOption.IGNORE_CASE).find(mensaje) ?: return null
    val bytes = match.groupValues[1].toIntOrNull() ?: return null
    return bytes.takeIf { it in 8_192..20_000_000 }
}

fun interface CompresorFoto {
    fun jpeg(origen: ByteArray, limiteBytes: Int): ByteArray?
}
