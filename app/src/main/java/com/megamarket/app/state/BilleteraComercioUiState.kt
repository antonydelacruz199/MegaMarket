package com.megamarket.app.state

import com.megamarket.app.data.repository.BilleteraComercio

data class BilleteraComercioUiState(
    val cargando: Boolean = false,
    val billetera: BilleteraComercio? = null,
    val error: String? = null
)
