package com.megamarket.cliente.data.remote.dto

data class CrearPedidoResponse(
    val id: String,
    val clientUuid: String,
    val version: Long? = null,
    val duplicado: Boolean? = null
)
