package com.megamarket.picker.data.repository

/**
 * Disponible para tomar: RECIBIDO (flujo nuevo, picking antes del cobro).
 * Legado: CONFIRMADO CONTRAENTREGA o sin pago; PAGADO (ONLINE cobrado en flujo antiguo).
 */
fun puedePasarAEnPicking(estado: String?, pago: PagoDtoConsulta?): Boolean {
    val actual = estado?.trim().orEmpty()
    if (actual == "RECIBIDO") return true
    if (actual == "PAGADO") return true
    if (actual != "CONFIRMADO") return false
    // Legado pre-RECIBIDO: CONFIRMADO aún se podía tomar si no era ONLINE pendiente.
    if (pago == null) return true
    return pago.modalidad?.trim().equals("CONTRAENTREGA", ignoreCase = true)
}

data class PagoDtoConsulta(
    val modalidad: String?
)
