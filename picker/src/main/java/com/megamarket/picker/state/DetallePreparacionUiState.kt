package com.megamarket.picker.state

import com.megamarket.picker.data.repository.PreparacionPedido

data class DetallePreparacionUiState(
    val cargando: Boolean = true,
    val preparacion: PreparacionPedido? = null,
    val error: String? = null,
    val codigoError: String? = null,
    val marcandoListo: Boolean = false,
    val resolviendoDetalleId: String? = null,
    val listoOk: Boolean = false,
    val sesionVencida: Boolean = false
)
