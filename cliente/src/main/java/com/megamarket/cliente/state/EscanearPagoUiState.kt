package com.megamarket.cliente.state

data class EscanearPagoUiState(
    val pedidoId: String = "",
    val escaneando: Boolean = true,
    val confirmando: Boolean = false,
    val confirmado: Boolean = false,
    val ultimoCodigo: String? = null,
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false,
    val pedidoEstado: String? = null,
    val saldoClienteCentimos: Long? = null
)
