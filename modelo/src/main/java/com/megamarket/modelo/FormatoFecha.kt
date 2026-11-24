package com.megamarket.modelo

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Zona de tienda / servidor (Perú). */
val ZONA_PERU: TimeZone = TimeZone.getTimeZone("America/Lima")

private val LOCALE_PE: Locale = Locale.Builder().setLanguage("es").setRegion("PE").build()

/**
 * Normaliza a ISO `yyyy-MM-dd`.
 * Acepta ISO o presentación Perú `dd/MM/yyyy`.
 */
fun normalizarFechaIso(crudo: String?): String? {
    val texto = crudo?.trim().orEmpty()
    if (texto.isEmpty()) return null
    val base = texto.substringBefore('T').substringBefore(' ').take(10)
    if (base.length == 10 && base[4] == '-' && base[7] == '-') return base
    if (base.length == 10 && base[2] == '/' && base[5] == '/') {
        val dia = base.substring(0, 2)
        val mes = base.substring(3, 5)
        val anio = base.substring(6, 10)
        if (dia.toIntOrNull() in 1..31 && mes.toIntOrNull() in 1..12 && anio.length == 4) {
            return "$anio-$mes-$dia"
        }
    }
    return null
}

/** Solo fecha en pantalla: `dd/MM/yyyy`. */
fun formatearFechaPresentacion(crudo: String?): String {
    val texto = crudo?.trim().orEmpty()
    if (texto.isEmpty()) return ""
    val iso = normalizarFechaIso(texto)
    if (iso != null) {
        val partes = iso.split('-')
        if (partes.size == 3) return "${partes[2]}/${partes[1]}/${partes[0]}"
    }
    val millis = parsearInstanteAMillis(texto) ?: return texto.replace('T', ' ').take(10)
    return SimpleDateFormat("dd/MM/yyyy", LOCALE_PE).apply {
        timeZone = ZONA_PERU
    }.format(Date(millis))
}

/** Fecha y hora en pantalla (Perú): `dd/MM/yyyy HH:mm`. */
fun formatearFechaHoraPresentacion(crudo: String?): String {
    val texto = crudo?.trim().orEmpty()
    if (texto.isEmpty()) return "—"
    val millis = parsearInstanteAMillis(texto)
        ?: return texto.replace('T', ' ').take(16)
    return formatearMillisFechaHora(millis)
}

fun formatearMillisFechaHora(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", LOCALE_PE).apply {
        timeZone = ZONA_PERU
    }.format(Date(millis))

/**
 * Interpreta timestamps del servidor.
 * Sin zona explícita → reloj de Perú; con Z/offset → UTC/offset indicado.
 */
fun parsearInstanteAMillis(iso: String?): Long? {
    val crudo = iso?.trim().orEmpty()
    if (crudo.isEmpty()) return null
    val conT = crudo.replace(" ", "T")
    val tieneZona = conT.endsWith("Z", ignoreCase = true) ||
        conT.endsWith("+00:00") ||
        Regex("""[+-]\d{2}:?\d{2}$""").containsMatchIn(conT)

    if (!tieneZona) {
        val patronesLocales = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "dd/MM/yyyy'T'HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "yyyy-MM-dd"
        )
        for (patron in patronesLocales) {
            try {
                val sdf = SimpleDateFormat(patron, Locale.US)
                sdf.timeZone = ZONA_PERU
                sdf.isLenient = false
                return sdf.parse(conT)?.time
            } catch (_: ParseException) {
                // siguiente
            }
        }
        return null
    }

    val normalizado = if (conT.endsWith("+00:00")) conT.dropLast(6) + "Z" else conT
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
            // siguiente
        }
    }
    return null
}
