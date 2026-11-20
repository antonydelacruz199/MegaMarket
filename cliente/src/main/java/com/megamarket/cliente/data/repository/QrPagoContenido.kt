package com.megamarket.cliente.data.repository

/**
 * Contenido del QR de cobro emitido por el servidor (`construirQrContenido`).
 * Formato multilínea: MEGAMARKET-PAGO + pares clave=valor.
 */
data class QrPagoEscaneado(
    val pedidoId: String,
    val pagoId: String,
    val referencia: String?,
    val montoCentimos: Long?,
    val modalidad: String?
)

fun parsearQrPago(contenido: String): QrPagoEscaneado? {
    val lineas = contenido.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lineas.isEmpty() || !lineas.first().equals("MEGAMARKET-PAGO", ignoreCase = true)) {
        return null
    }
    val mapa = linkedMapOf<String, String>()
    for (linea in lineas.drop(1)) {
        val sep = linea.indexOf('=')
        if (sep <= 0) continue
        mapa[linea.substring(0, sep).trim().lowercase()] = linea.substring(sep + 1).trim()
    }
    val pedidoId = mapa["pedido"]?.trim().orEmpty()
    val pagoId = mapa["pago"]?.trim().orEmpty()
    if (pedidoId.isEmpty() || pagoId.isEmpty()) return null
    return QrPagoEscaneado(
        pedidoId = pedidoId,
        pagoId = pagoId,
        referencia = mapa["ref"]?.takeIf { it.isNotEmpty() },
        montoCentimos = mapa["monto_centimos"]?.toLongOrNull(),
        modalidad = mapa["modalidad"]?.uppercase()
    )
}
