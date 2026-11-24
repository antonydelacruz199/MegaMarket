package com.megamarket.picker.ui

import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles as formatearSolesModelo

fun Long.formatearSoles(): String = formatearSolesModelo()

fun textoFecha(fecha: String?): String {
    if (fecha.isNullOrBlank()) return ""
    return formatearFechaHoraPresentacion(fecha).let { if (it == "—") "" else it }
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
    "SIN_STOCK" -> "Sin stock en almacén"
    else -> estado.orEmpty()
}
