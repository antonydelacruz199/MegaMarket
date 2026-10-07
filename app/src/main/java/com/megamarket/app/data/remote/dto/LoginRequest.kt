package com.megamarket.app.data.remote.dto

data class LoginRequest(
    val usuario: String,
    val clave: String,
    val deviceName: String? = "MegaMarket Admin Android"
)
