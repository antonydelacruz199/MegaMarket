package com.megamarket.picker.data.repository

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun epochMillisIso(valor: String): Long? {
    val texto = valor.trim()
    if (texto.isEmpty()) return null
    val patrones = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )
    for (patron in patrones) {
        try {
            val formato = SimpleDateFormat(patron, Locale.US)
            formato.timeZone = TimeZone.getTimeZone("UTC")
            formato.isLenient = false
            return formato.parse(texto)?.time
        } catch (_: ParseException) {
            continue
        }
    }
    return null
}

internal fun tokenSigueVigente(expiresAt: String?, ahoraMillis: Long): Boolean {
    if (expiresAt.isNullOrBlank()) return true
    val fin = epochMillisIso(expiresAt) ?: return true
    return ahoraMillis < fin
}
