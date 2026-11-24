package com.megamarket.app.state

import com.megamarket.app.data.repository.BilleteraComercio
import com.megamarket.app.data.repository.MovimientoComercio

data class BilleteraComercioUiState(
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val billetera: BilleteraComercio? = null,
    val movimientos: List<MovimientoComercio> = emptyList(),
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val error: String? = null
)
