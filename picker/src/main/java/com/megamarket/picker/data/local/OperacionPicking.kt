package com.megamarket.picker.data.local

import com.megamarket.modelo.EstadoSincronizacion

object OperacionPicking {
    const val TIPO_DETALLE = "PICKING_DETALLE"
    const val ESCANEAR = "ESCANEAR"
    const val MARCAR_FALTANTE = "MARCAR_FALTANTE"

    const val PENDIENTE = EstadoSincronizacion.PENDIENTE
    const val ENVIANDO = EstadoSincronizacion.ENVIANDO
    const val SINCRONIZADO = EstadoSincronizacion.SINCRONIZADO
    const val ERROR = EstadoSincronizacion.ERROR

    const val RESULTADO_CORRECTO = "CORRECTO"
    const val RESULTADO_INCORRECTO = "INCORRECTO"

    /** Pendientes de resolución (incluye rechazos definitivos visibles). */
    val ACTIVAS: Set<String> = EstadoSincronizacion.EN_COLA

    /** Aún no tienen respuesta definitiva del servidor. */
    val EN_VUELO: Set<String> = setOf(PENDIENTE, ENVIANDO)
}
