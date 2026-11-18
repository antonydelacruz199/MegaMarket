package com.megamarket.picker.data.remote

data class LoginRequest(
    val usuario: String,
    val clave: String,
    val deviceName: String
)

data class LoginResponse(
    val accessToken: String?,
    val tokenType: String?,
    val expiresAt: String?,
    val usuario: UsuarioApiDto?
)

data class MeResponse(
    val usuario: UsuarioApiDto?
)

data class UsuarioApiDto(
    val id: String?,
    val nombre: String?,
    val usuario: String?,
    val correo: String?,
    val rol: String?,
    val version: Long?
)
