package com.megamarket.picker.data.repository

import com.megamarket.modelo.parsearInstanteAMillis
import java.util.Locale
import kotlin.math.max

/**
 * Calcula el tiempo restante a partir de [fechaLimite] del servidor.
 * No decide ni resuelve la sustitución localmente.
 */
fun milisRestantesHasta(fechaLimite: String?, ahoraMillis: Long = System.currentTimeMillis()): Long {
    val limite = parsearIsoAMillis(fechaLimite) ?: return 0L
    return max(0L, limite - ahoraMillis)
}

fun formatearTiempoRestante(millis: Long): String {
    val totalSegundos = max(0L, millis / 1000L)
    val minutos = totalSegundos / 60L
    val segundos = totalSegundos % 60L
    return "%d:%02d".format(Locale.US, minutos, segundos)
}

fun parsearIsoAMillis(iso: String?): Long? = parsearInstanteAMillis(iso)
