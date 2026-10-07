package com.megamarket.modelo

/**
 * Estados de sincronización (RF12–RF15).
 * Independientes del estado de negocio (p. ej. Pedido.CONFIRMADO).
 */
object EstadoSincronizacion {
    const val PENDIENTE = "PENDIENTE"
    const val ENVIANDO = "ENVIANDO"
    const val SINCRONIZADO = "SINCRONIZADO"
    const val ERROR = "ERROR"

    /** Estados que aún deben enviarse o reintentarse. */
    val EN_COLA: Set<String> = setOf(PENDIENTE, ENVIANDO, ERROR)

    fun esActiva(estado: String): Boolean = estado in EN_COLA
}
