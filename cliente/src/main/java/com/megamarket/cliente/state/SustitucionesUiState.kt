package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.LineaPedidoCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente

data class SustitucionesUiState(
    val cargando: Boolean = true,
    val cargandoMas: Boolean = false,
    val propuestas: List<PropuestaSustitucionCliente> = emptyList(),
    val pagina: Int = 1,
    val hayMas: Boolean = false,
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
    val pedidoLocalId: Long? = null,
    val lineasDisponibles: List<LineaPedidoCliente> = emptyList(),
    val lineasFaltantes: List<LineaPedidoCliente> = emptyList(),
    val estadoPedido: String? = null,
    val puedeCancelarPedido: Boolean = false,
    val cancelandoPedido: Boolean = false,
    val pedidoCancelado: Boolean = false
)
