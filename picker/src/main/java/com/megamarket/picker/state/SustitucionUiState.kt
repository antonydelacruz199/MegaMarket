package com.megamarket.picker.state

import com.megamarket.picker.data.repository.CandidatoSustituto
import com.megamarket.picker.data.repository.SustitucionPropuesta

data class SustitucionUiState(
    val cargando: Boolean = true,
    val enviando: Boolean = false,
    val resolviendo: Boolean = false,
    val consultandoEstado: Boolean = false,
    val faltantePendienteLocal: Boolean = false,
    val nombreProductoOriginal: String = "",
    val cantidadLinea: Int = 0,
    val precioOriginalCentimos: Long = 0L,
    val candidatos: List<CandidatoSustituto> = emptyList(),
    val seleccionadoId: String? = null,
    val propuesta: SustitucionPropuesta? = null,
    val tiempoRestanteTexto: String? = null,
    val tiempoRestanteMillis: Long = 0L,
    val error: String? = null,
    val codigoError: String? = null,
    val detalleActualizado: Boolean = false,
    val lineaResuelta: Boolean = false,
    val sesionVencida: Boolean = false,
    val sinStockAlmacen: Boolean = false,
    val avisoSinStockEnviado: Boolean = false
)
