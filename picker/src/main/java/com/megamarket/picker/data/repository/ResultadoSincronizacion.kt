package com.megamarket.picker.data.repository

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val mensaje: String) : ResultadoSincronizacion()
}

sealed class ResultadoEnvio {
    data object Ok : ResultadoEnvio()
    data class ErrorDefinitivo(val mensaje: String, val codigo: String? = null) : ResultadoEnvio()
    data class Reintentar(val mensaje: String) : ResultadoEnvio()
    data class ConfirmadoIncorrecto(val mensaje: String?) : ResultadoEnvio()
    data class ConfirmadoCorrecto(
        val armada: Int?,
        val estadoPicking: String?,
        val mensaje: String?
    ) : ResultadoEnvio()
}
