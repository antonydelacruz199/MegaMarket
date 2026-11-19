package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.MODALIDAD_CONTRAENTREGA
import com.megamarket.cliente.data.repository.MODALIDAD_ONLINE
import com.megamarket.cliente.model.Pedido

data class ConfirmacionUiState(
    val cargando: Boolean = true,
    val sincronizando: Boolean = false,
    val cancelando: Boolean = false,
    val pedido: Pedido? = null,
    val error: String? = null,
    val totalPropuesto: Long? = null,
    /** Estado del pago leído del servidor (mejor esfuerzo); `null` si no se pudo consultar. */
    val pagoConfirmado: Boolean? = null
) {
    val esOnline: Boolean
        get() = pedido?.modalidadPago.equals(MODALIDAD_ONLINE, ignoreCase = true)

    val esContraentrega: Boolean
        get() = pedido?.modalidadPago.equals(MODALIDAD_CONTRAENTREGA, ignoreCase = true)

    /** ONLINE solo cuando el pedido está CONFIRMADO (preparación validada). */
    val listoParaPagarOnline: Boolean
        get() {
            val p = pedido ?: return false
            return esOnline &&
                p.estado == Pedido.ESTADO_CONFIRMADO &&
                !p.remoteId.isNullOrBlank() &&
                pagoConfirmado != true
        }

    /** CONTRAENTREGA: el cliente puede pagar con billetera en entrega. */
    val listoParaPagarContraentrega: Boolean
        get() {
            val p = pedido ?: return false
            val e = p.estado.trim().uppercase()
            return esContraentrega &&
                !p.remoteId.isNullOrBlank() &&
                pagoConfirmado != true &&
                (e == "LISTO" || e == "EN_CAMINO")
        }

    /** Pedido en servidor pero aún en preparación (no cobrable). */
    val enPreparacion: Boolean
        get() {
            val p = pedido ?: return false
            val e = p.estado.trim().uppercase()
            return e == "RECIBIDO" || e == "EN_PICKING"
        }

    val esperandoSincronizacionPago: Boolean
        get() {
            val p = pedido ?: return false
            if (pagoConfirmado == true) return false
            if (p.estado == "PENDIENTE_CONFIRMACION") return true
            return p.remoteId.isNullOrBlank()
        }
}
