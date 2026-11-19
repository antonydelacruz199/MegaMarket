package com.megamarket.cliente.state

import com.megamarket.cliente.model.Pedido

data class ConfirmacionUiState(
    val cargando: Boolean = true,
    val sincronizando: Boolean = false,
    val cancelando: Boolean = false,
    val pedido: Pedido? = null,
    val error: String? = null,
    val totalPropuesto: Long? = null,
    /** Estado del pago ONLINE leído del servidor (mejor esfuerzo); `null` si no se pudo consultar. */
    val pagoConfirmado: Boolean? = null
)
