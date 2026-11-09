package com.megamarket.cliente.data.local

interface SesionSincronizacion {
    val owner: kotlinx.coroutines.flow.StateFlow<Long>
    val usuarioRemotoId: String?
    val accessToken: String?
}
