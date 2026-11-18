package com.megamarket.picker.state

import com.megamarket.picker.data.repository.PedidoAsignado
import com.megamarket.picker.data.repository.PedidoDisponible

data class ListaPickerUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val disponibles: List<PedidoDisponible> = emptyList(),
    val asignados: List<PedidoAsignado> = emptyList(),
    val error: String? = null,
    val codigoError: String? = null,
    val tomandoId: String? = null,
    val pedidoTomadoId: String? = null,
    val sesionVencida: Boolean = false
)
