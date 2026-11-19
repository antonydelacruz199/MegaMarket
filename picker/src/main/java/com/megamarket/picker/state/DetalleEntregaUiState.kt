package com.megamarket.picker.state

import com.megamarket.picker.data.repository.DetalleReparto
import com.megamarket.picker.data.repository.PagoQrReparto

data class DetalleEntregaUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val detalle: DetalleReparto? = null,
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false,
    val conflicto: Boolean = false,
    val fotoLista: Boolean = false,
    val revisionFoto: Int = 0,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val registrando: Boolean = false,
    val entregado: Boolean = false,
    val confirmacionReciente: Boolean = false,
    val idempotente: Boolean = false,
    val pagoPendiente: Boolean = false,
    /** QR simulado de contraentrega vigente; mostrarlo no desbloquea la entrega. */
    val qrPago: PagoQrReparto? = null,
    val generandoQr: Boolean = false,
    val confirmandoPago: Boolean = false,
    val errorPago: String? = null,
    /** Solo `true` cuando el servidor confirmó el pago y el detalle recargado ya no lo marca pendiente. */
    val pagoConfirmadoReciente: Boolean = false
) {
    /** Contraentrega EN_CAMINO con pago sin confirmar: el repartidor debe cobrar con el QR simulado. */
    val requierePagoQr: Boolean
        get() {
            val actual = detalle ?: return false
            return !entregado && pagoPendiente &&
                actual.pago?.esContraentrega == true &&
                actual.pedido.estado.equals("EN_CAMINO", ignoreCase = true)
        }
}
