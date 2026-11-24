package com.megamarket.picker.state

import com.megamarket.picker.data.repository.PedidoAsignado
import com.megamarket.picker.data.repository.PedidoDespacho
import com.megamarket.picker.data.repository.PedidoDisponible
import com.megamarket.picker.ui.navigation.SeccionPicker

data class ListaPickerUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val cargandoMas: Boolean = false,
    val seccion: SeccionPicker = SeccionPicker.DISPONIBLES,
    val disponibles: List<PedidoDisponible> = emptyList(),
    val asignados: List<PedidoAsignado> = emptyList(),
    val despachables: List<PedidoDespacho> = emptyList(),
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val error: String? = null,
    val codigoError: String? = null,
    val tomandoId: String? = null,
    val despachandoId: String? = null,
    val pedidoTomadoId: String? = null,
    val sesionVencida: Boolean = false
)
