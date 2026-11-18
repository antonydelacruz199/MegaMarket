package com.megamarket.picker.data.repository

enum class TipoResultadoEscaneo {
    CORRECTO,
    INCORRECTO,
    CANTIDAD_EXCEDIDA,
    PEDIDO_NO_ASIGNADO,
    SESION_VENCIDA,
    PENDIENTE_LOCAL,
    RECHAZADO
}

sealed class ResultadoEscaneo {
    data class Confirmado(
        val tipo: TipoResultadoEscaneo,
        val mensaje: String?,
        val codigo: String?,
        val linea: LineaPreparacion?,
        val vibrar: Boolean,
        val cantidadPendienteLocal: Int = 0
    ) : ResultadoEscaneo()

    data class PendienteLocal(
        val mensaje: String?,
        val cantidadArmada: Int,
        val cantidadPendienteLocal: Int,
        val estadoLinea: String
    ) : ResultadoEscaneo()

    data class Fallo(
        val tipo: TipoErrorPedido,
        val mensaje: String,
        val codigo: String?,
        val conservarOperacion: Boolean
    ) : ResultadoEscaneo()
}
