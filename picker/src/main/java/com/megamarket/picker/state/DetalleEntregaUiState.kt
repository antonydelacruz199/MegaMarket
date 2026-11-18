package com.megamarket.picker.state

import com.megamarket.picker.data.repository.DetalleReparto

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
    val pagoPendiente: Boolean = false
)
