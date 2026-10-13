package com.megamarket.cliente.state

import com.megamarket.modelo.Usuario

data class SesionUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val usuario: Usuario? = null
)
