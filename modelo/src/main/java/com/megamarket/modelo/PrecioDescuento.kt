package com.megamarket.modelo

/**
 * Cálculo de precio con descuento porcentual (RF09).
 * Dinero siempre en céntimos (Long). Redondeo: división entera hacia abajo.
 *
 * [descuentoPorcentaje] es la fuente principal.
 * [precioOfertaCentimos] se mantiene por compatibilidad con Neon/UI legacy
 * y debe derivarse de este porcentaje al persistir.
 */
object PrecioDescuento {

    fun esDescuentoValido(descuentoPorcentaje: Int): Boolean =
        descuentoPorcentaje in 0..100

    fun esOfertaActiva(esOferta: Boolean, descuentoPorcentaje: Int): Boolean =
        esOferta && descuentoPorcentaje in 1..99

    /**
     * precioCentimos * (100 - descuento) / 100
     * División entera (floor). Resultado ≥ 0.
     */
    fun precioConDescuentoCentimos(precioCentimos: Long, descuentoPorcentaje: Int): Long {
        require(precioCentimos >= 0) { "precioCentimos negativo" }
        require(esDescuentoValido(descuentoPorcentaje)) { "descuento fuera de rango" }
        if (descuentoPorcentaje == 0 || descuentoPorcentaje == 100) {
            return if (descuentoPorcentaje == 100) 0L else precioCentimos
        }
        return precioCentimos * (100L - descuentoPorcentaje) / 100L
    }

    /** Deriva % desde precio de oferta legacy (compatibilidad). */
    fun descuentoDesdePrecioOferta(precioCentimos: Long, precioOfertaCentimos: Long?): Int {
        if (precioOfertaCentimos == null || precioCentimos <= 0L) return 0
        if (precioOfertaCentimos <= 0L || precioOfertaCentimos >= precioCentimos) return 0
        val pct = ((precioCentimos - precioOfertaCentimos) * 100L / precioCentimos).toInt()
        return pct.coerceIn(1, 99)
    }

    /** Deriva precio oferta desde % (compatibilidad Neon / Provider). */
    fun precioOfertaDesdeDescuento(precioCentimos: Long, descuentoPorcentaje: Int): Long? {
        if (!esOfertaActiva(true, descuentoPorcentaje)) return null
        val vigente = precioConDescuentoCentimos(precioCentimos, descuentoPorcentaje)
        return if (vigente > 0L && vigente < precioCentimos) vigente else null
    }
}
