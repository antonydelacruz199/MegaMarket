package com.megamarket.cliente.model

data class Direccion(
    val departamento: String,
    val provincia: String,
    val distrito: String,
    val direccion: String,
    val telefono: String
) {
    fun error(): String? = when {
        departamento.isBlank() -> "Ingresa el departamento"
        provincia.isBlank() -> "Ingresa la provincia"
        distrito.isBlank() -> "Ingresa el distrito"
        direccion.isBlank() -> "Ingresa la dirección"
        telefono.count { it.isDigit() } < 6 -> "Ingresa un teléfono válido"
        else -> null
    }
}
