package com.megamarket.cliente.estado

data class EstadoUiCompra(
    val departamento: String = "",
    val provincia: String = "",
    val distrito: String = "",
    val direccion: String = "",
    val telefono: String = "",
    val error: String? = null,
    val confirmado: Boolean = false
)
