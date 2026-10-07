package com.megamarket.cliente.data.remote.dto

/** Preparado para API futura. El login actual sigue siendo local. */
data class LoginRequest(
    val usuario: String,
    val clave: String
)
