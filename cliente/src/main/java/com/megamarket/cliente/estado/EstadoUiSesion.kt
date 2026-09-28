package com.megamarket.cliente.estado

import com.megamarket.modelo.Usuario

data class EstadoUiSesion(
    val cargando: Boolean = false,
    val error: String? = null,
    val usuario: Usuario? = null
)
