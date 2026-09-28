package com.megamarket.app.estado

import com.megamarket.modelo.Usuario

data class EstadoUiSesion(
    val cargando: Boolean = false,
    val error: String? = null,
    val usuario: Usuario? = null
)
