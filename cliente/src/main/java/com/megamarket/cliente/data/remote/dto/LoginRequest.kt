package com.megamarket.cliente.data.remote.dto

data class LoginRequest(
    val usuario: String,
    val clave: String,
    val deviceName: String? = "MegaMarket Cliente Android"
)
