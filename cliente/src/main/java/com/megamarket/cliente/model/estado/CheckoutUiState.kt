package com.megamarket.cliente.model.estado

data class CheckoutUiState(
    val departamento: String = "",
    val provincia: String = "",
    val distrito: String = "",
    val direccion: String = "",
    val telefono: String = "",

    val cargando: Boolean = false,
    val error: String? = null,

    val pedidoCreadoId: Long? = null
)
