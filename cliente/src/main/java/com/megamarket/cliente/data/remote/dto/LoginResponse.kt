package com.megamarket.cliente.data.remote.dto

data class LoginResponse(
    val id: String,
    val nombre: String,
    val usuario: String,
    val correo: String? = null,
    val token: String? = null
)
