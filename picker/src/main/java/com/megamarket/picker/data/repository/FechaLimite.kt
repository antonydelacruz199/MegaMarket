package com.megamarket.picker.data.repository

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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

fun parsearIsoAMillis(iso: String?): Long? {
    val crudo = iso?.trim().orEmpty()
    if (crudo.isEmpty()) return null
    val normalizado = crudo
        .replace(" ", "T")
        .let { if (it.endsWith("+00:00")) it.dropLast(6) + "Z" else it }
        .let { if (it.endsWith("Z")) it else if (it.contains('+') || it.lastIndexOf('-') > 10) it else it + "Z" }

    val patrones = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ"
    )
    for (patron in patrones) {
        try {
            val sdf = SimpleDateFormat(patron, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.isLenient = false
            val valor = if (patron.endsWith("Z") && !patron.contains("'Z'")) {
                normalizado.replace("Z", "+0000")
            } else {
                normalizado
            }
            return sdf.parse(valor)?.time
        } catch (_: ParseException) {
            // probar siguiente
        }
    }
    return null
}
