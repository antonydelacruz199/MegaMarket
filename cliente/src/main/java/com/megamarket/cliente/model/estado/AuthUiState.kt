package com.megamarket.cliente.model.estado

import com.megamarket.modelo.Usuario

data class AuthUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val usuario: Usuario? = null
)
