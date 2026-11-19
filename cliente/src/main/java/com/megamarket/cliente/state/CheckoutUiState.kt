package com.megamarket.cliente.state

import com.megamarket.cliente.model.FranjaEntrega
import com.megamarket.cliente.model.ModalidadPagoCheckout

enum class PasoCheckout {
    DIRECCION,
    FRANJA,
    PAGO,
    RESUMEN
}

data class CheckoutUiState(
    val paso: PasoCheckout = PasoCheckout.DIRECCION,

    val departamento: String = "",
    val provincia: String = "",
    val distrito: String = "",
    val direccion: String = "",
    val telefono: String = "",

    val franjas: List<FranjaEntrega> = emptyList(),
    val franjaSeleccionadaId: String? = null,
    val fechasDisponibles: List<String> = emptyList(),
    val fechaSeleccionada: String? = null,
    val cargandoFranjas: Boolean = false,
    val errorFranjas: String? = null,

    val modalidadPago: ModalidadPagoCheckout? = null,

    val cargando: Boolean = false,
    val error: String? = null,

    val pedidoCreadoId: Long? = null
) {
    val franjaSeleccionada: FranjaEntrega?
        get() = franjas.firstOrNull { it.id == franjaSeleccionadaId }

    val franjasDeFecha: List<FranjaEntrega>
        get() = franjas.filter { it.fechaIso == fechaSeleccionada }
}
