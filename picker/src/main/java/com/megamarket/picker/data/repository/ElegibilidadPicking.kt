package com.megamarket.picker.data.repository

/**
 * Un CONFIRMADO solo puede pasar a EN_PICKING si no tiene pago (legado)
 * o si la modalidad es CONTRAENTREGA. ONLINE en CONFIRMADO queda fuera:
 * el servidor exige el estado PAGADO, que solo fija el banco.
 * PAGADO ya trae el cobro confirmado y sí se puede tomar.
 */
fun puedePasarAEnPicking(estado: String?, pago: PagoDtoConsulta?): Boolean {
    val actual = estado?.trim().orEmpty()
    if (actual == "PAGADO") return true
    if (actual != "CONFIRMADO") return false
    if (pago == null) return true
    return pago.modalidad?.trim().equals("CONTRAENTREGA", ignoreCase = true)
}

data class PagoDtoConsulta(
    val modalidad: String?
)
