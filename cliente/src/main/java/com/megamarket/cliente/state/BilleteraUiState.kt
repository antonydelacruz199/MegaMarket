package com.megamarket.cliente.state

import com.megamarket.cliente.data.repository.CuentaBilleteraCliente
import com.megamarket.cliente.data.repository.MovimientoBilleteraCliente

data class BilleteraUiState(
    val cargando: Boolean = true,
    val cuenta: CuentaBilleteraCliente? = null,
    val saldoInicialCentimos: Long? = null,
    val movimientos: List<MovimientoBilleteraCliente> = emptyList(),
    val error: String? = null,
    val sesionVencida: Boolean = false
)
