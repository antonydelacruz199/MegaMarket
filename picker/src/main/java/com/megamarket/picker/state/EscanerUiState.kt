package com.megamarket.picker.state

import com.megamarket.picker.data.repository.ResumenOperacionLocal
import com.megamarket.picker.data.repository.TipoResultadoEscaneo

data class ResumenProgresoLinea(
    val id: String,
    val nombre: String,
    val cantidadSolicitada: Int,
    val cantidadArmada: Int,
    val estadoLinea: String
)

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
    val operaciones: List<ResumenOperacionLocal> = emptyList(),
    val lineaCompleta: Boolean = false,
    val pedidoListoParaConfirmar: Boolean = false,
    val confirmandoPedido: Boolean = false,
    val pedidoConfirmado: Boolean = false,
    val navegarASustitucion: Boolean = false,
    val lineasEscaneadas: List<ResumenProgresoLinea> = emptyList(),
    val lineasPendientes: List<ResumenProgresoLinea> = emptyList()
) {
    val unidadesEfectivas: Int
        get() = cantidadArmada + cantidadPendienteLocal

    fun recalcularLineaCompleta(): Boolean {
        if (cantidadSolicitada <= 0) return false
        val estado = estadoLinea.trim().uppercase()
        if (estado == "FALTANTE" || estado == "ELIMINADO") return true
        return unidadesEfectivas >= cantidadSolicitada
    }
}
