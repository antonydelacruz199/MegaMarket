package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.FiltroMisPedidos
import com.megamarket.cliente.data.repository.PedidoDetalleCliente
import com.megamarket.cliente.data.repository.PedidoResumenCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente

data class MisPedidosUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val filtro: FiltroMisPedidos = FiltroMisPedidos.TODOS,
    val pedidos: List<PedidoResumenCliente> = emptyList(),
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false
)

data class DetallePedidoUiState(
    val cargando: Boolean = true,
    val detalle: PedidoDetalleCliente? = null,
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false,
    val decidiendoId: String? = null,
    val ahoraEpochMs: Long = System.currentTimeMillis(),
    val cancelando: Boolean = false,
    val cancelado: Boolean = false,
    val autorizandoBilletera: Boolean = false,
    val autorizacionBilleteraOk: Boolean = false
) {
    val propuestasPendientes: List<PropuestaSustitucionCliente>
        get() = detalle?.sustituciones.orEmpty().filter {
            it.estado.equals("PENDIENTE", ignoreCase = true)
        }
}
