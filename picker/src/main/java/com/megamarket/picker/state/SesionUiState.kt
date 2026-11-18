package com.megamarket.picker.state

import com.megamarket.picker.model.UsuarioOperativo

data class SesionUiState(
    val restaurando: Boolean = true,
    val cargando: Boolean = false,
    val error: String? = null,
    val usuario: UsuarioOperativo? = null
)
