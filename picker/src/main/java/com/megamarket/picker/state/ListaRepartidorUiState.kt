package com.megamarket.picker.state

import com.megamarket.picker.data.repository.PedidoReparto
import com.megamarket.picker.ui.navigation.SeccionReparto

data class ListaRepartidorUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val cargandoMas: Boolean = false,
    val seccion: SeccionReparto = SeccionReparto.EN_CAMINO,
    val pedidos: List<PedidoReparto> = emptyList(),
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val total: Int = 0,
    val error: String? = null,
    val codigoError: String? = null,
    val tomandoId: String? = null,
    val pedidoTomadoId: String? = null,
    val sesionVencida: Boolean = false
) {
    /** Compat con pantallas/tests antiguos. */
    val disponibles: List<PedidoReparto>
        get() = if (seccion == SeccionReparto.LISTOS) pedidos else emptyList()
    val enCamino: List<PedidoReparto>
        get() = if (seccion == SeccionReparto.EN_CAMINO) pedidos else emptyList()
    val entregados: List<PedidoReparto>
        get() = if (seccion == SeccionReparto.ENTREGADOS) pedidos else emptyList()
}
