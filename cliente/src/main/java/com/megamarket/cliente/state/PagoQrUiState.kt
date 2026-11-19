package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.PagoCliente
import com.megamarket.cliente.data.repository.QrPagoCliente

data class PagoQrUiState(
    val cargando: Boolean = true,
    val confirmando: Boolean = false,
    val consultandoEstado: Boolean = false,
    val pedidoId: String = "",
    val pago: PagoCliente? = null,
    val qr: QrPagoCliente? = null,
    val pedidoEstado: String? = null,
    val saldoClienteCentimos: Long? = null,
    /** Solo es `true` cuando el servidor declaró el pago CONFIRMADO. */
    val confirmado: Boolean = false,
    val error: String? = null,
    val codigoError: String? = null,
    val sesionVencida: Boolean = false
)
