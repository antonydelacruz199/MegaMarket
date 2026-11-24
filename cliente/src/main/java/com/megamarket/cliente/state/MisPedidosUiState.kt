package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.FiltroMisPedidos
import com.megamarket.cliente.data.repository.PedidoDetalleCliente
import com.megamarket.cliente.data.repository.PedidoResumenCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente

data class MisPedidosUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val cargandoMas: Boolean = false,
    val filtro: FiltroMisPedidos = FiltroMisPedidos.TODOS,
    val consulta: String = "",
    val pedidos: List<PedidoResumenCliente> = emptyList(),
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val total: Int = 0,
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
    val cancelado: Boolean = false
) {
    val propuestasPendientes: List<PropuestaSustitucionCliente>
        get() = detalle?.sustituciones.orEmpty().filter {
            EstadoSustitucion.esRequiereAtencion(it.estado)
        }
}
