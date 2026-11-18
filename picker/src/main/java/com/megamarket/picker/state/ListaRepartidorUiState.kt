package com.megamarket.picker.state

import com.megamarket.picker.data.repository.PedidoReparto

data class ListaRepartidorUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val disponibles: List<PedidoReparto> = emptyList(),
    val enCamino: List<PedidoReparto> = emptyList(),
    val entregados: List<PedidoReparto> = emptyList(),
    val error: String? = null,
    val codigoError: String? = null,
    val tomandoId: String? = null,
    val pedidoTomadoId: String? = null,
    val sesionVencida: Boolean = false
)
