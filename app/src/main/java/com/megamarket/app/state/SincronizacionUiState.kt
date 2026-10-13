package com.megamarket.app.state

data class SincronizacionUiState(
    val pendientes: Int = 0,
    val errores: Int = 0,
    val textoUltimaSincronizacion: String = "Nunca",
    val ultimoError: String? = null,
    val conectado: Boolean = false,
    val sincronizando: Boolean = false
)
