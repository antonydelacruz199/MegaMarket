package com.megamarket.cliente.model

/** Resultado de pedir al administrador que descuente o restaure stock. */
sealed class ResultadoDescuentoStock {
    data class Exito(val stockFinal: Int) : ResultadoDescuentoStock()
    data class Fallo(val mensaje: String) : ResultadoDescuentoStock()
}
