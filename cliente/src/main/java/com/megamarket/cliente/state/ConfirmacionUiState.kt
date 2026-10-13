package com.megamarket.cliente.state

import com.megamarket.cliente.model.Pedido

data class ConfirmacionUiState(
    val cargando: Boolean = true,
    val pedido: Pedido? = null,
    val error: String? = null
)
