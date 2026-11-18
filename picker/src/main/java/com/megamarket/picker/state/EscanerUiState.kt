package com.megamarket.picker.state

import com.megamarket.picker.data.repository.ResumenOperacionLocal
import com.megamarket.picker.data.repository.TipoResultadoEscaneo

data class EscanerUiState(
    val cargando: Boolean = true,
    val nombreProducto: String = "",
    val cantidadSolicitada: Int = 0,
    val cantidadArmada: Int = 0,
    val cantidadPendienteLocal: Int = 0,
    val estadoLinea: String = "",
    val faltantePendiente: Boolean = false,
    val imagenUrl: String? = null,
    val enviando: Boolean = false,
    val resultado: TipoResultadoEscaneo? = null,
    val mensaje: String? = null,
    val vibrar: Boolean = false,
    val idVibracion: Int = 0,
    val puedeReintentar: Boolean = false,
    val sesionVencida: Boolean = false,
    val operaciones: List<ResumenOperacionLocal> = emptyList()
)
