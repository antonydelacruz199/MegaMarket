package com.megamarket.app.data.remote.dto

data class LoginResponse(
    val accessToken: String,
    val tokenType: String? = "Bearer",
    val expiresAt: String? = null,
    val usuario: UsuarioRemotoDto
)

data class UsuarioRemotoDto(
    val id: String,
    val nombre: String,
    val usuario: String,
    val correo: String? = null,
    val rol: String,
    val version: Long? = null
)
