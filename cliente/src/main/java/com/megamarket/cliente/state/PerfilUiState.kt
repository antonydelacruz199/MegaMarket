package com.megamarket.cliente.state

data class PerfilUiState(
    val nombre: String = "",
    val correo: String = "",
    val rol: String = "Cliente",
    val pedidos: List<com.megamarket.cliente.data.local.Pedido> = emptyList()
)
