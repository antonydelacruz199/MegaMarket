package com.megamarket.cliente.ui

import com.megamarket.cliente.data.repository.EstadoSustitucion

fun textoEstadoSustitucion(estado: String?): String = when (estado?.trim()?.uppercase()) {
    EstadoSustitucion.PENDIENTE -> "Pendiente de tu respuesta"
    EstadoSustitucion.ACEPTADA -> "Aceptada"
    EstadoSustitucion.RECHAZADA -> "Rechazada"
    EstadoSustitucion.CANCELADA -> "Cancelada"
    else -> estado?.trim().orEmpty().ifBlank { "Desconocido" }
}
