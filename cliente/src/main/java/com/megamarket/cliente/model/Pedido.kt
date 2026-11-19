package com.megamarket.cliente.model

data class Pedido(
    val id: Long,
    val clienteId: Long,
    val fecha: Long,
    val totalCentimos: Long,
    val estado: String,
    val detalles: List<PedidoDetalle>,
    val direccion: Direccion,
    val contratoVersion: Int = 2,
    val franjaEntregaId: String? = null,
    val modalidadPago: String? = null,
    val franjaFecha: String? = null,
    val franjaHoraInicio: String? = null,
    val franjaHoraFin: String? = null,
    /** Id del pedido en el servidor; nulo mientras no se haya sincronizado. */
    val remoteId: String? = null
) {
    val cantidadProductos: Int
        get() = detalles.sumOf { it.cantidad }

    val franjaEtiqueta: String?
        get() = FranjaEntrega.etiquetaDesdeCampos(franjaFecha, franjaHoraInicio, franjaHoraFin)

    val modalidadEtiqueta: String?
        get() = ModalidadPagoCheckout.desdeApi(modalidadPago)?.titulo

    companion object {
        const val ESTADO_RECIBIDO = "RECIBIDO"
        const val ESTADO_CONFIRMADO = "CONFIRMADO"
        const val CONTRATO_V3 = 3
    }
}
