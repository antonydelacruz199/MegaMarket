package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente

data class SustitucionesUiState(
    val cargando: Boolean = true,
    val propuestas: List<PropuestaSustitucionCliente> = emptyList(),
    val pendientes: Int = 0,
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false,
    val actualizando: Boolean = false
)

data class DetalleSustitucionUiState(
    val cargando: Boolean = true,
    val propuesta: PropuestaSustitucionCliente? = null,
    val enviando: Boolean = false,
    val consultandoEstado: Boolean = false,
    val tiempoRestanteMillis: Long = 0L,
    val tiempoRestanteTexto: String? = null,
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false,
    val detallePedidoActualizado: Boolean = false,
    val pedidoLocalId: Long? = null
)
