package com.megamarket.picker.ui

fun Long.formatearSoles(): String {
    val negativo = this < 0
    val absoluto = kotlin.math.abs(this)
    val soles = absoluto / 100
    val centimos = absoluto % 100
    val texto = "S/ $soles.${centimos.toString().padStart(2, '0')}"
    return if (negativo) "-$texto" else texto
}

fun textoFecha(fecha: String?): String {
    if (fecha.isNullOrBlank()) return ""
    return fecha.replace('T', ' ').take(16)
}

fun textoEstadoLinea(estado: String): String = when (estado) {
    "PENDIENTE" -> "Pendiente"
    "ARMADO" -> "Armado"
    "FALTANTE" -> "Faltante"
    "SUSTITUIDO" -> "Sustituido"
    "ELIMINADO" -> "Eliminado"
    else -> estado
}

fun textoEstadoSustitucion(estado: String?): String = when (estado?.trim()?.uppercase()) {
    "PENDIENTE" -> "Pendiente"
    "ACEPTADA" -> "Aceptada"
    "RECHAZADA" -> "Rechazada"
    "CANCELADA" -> "Cancelada"
    else -> estado.orEmpty()
}
