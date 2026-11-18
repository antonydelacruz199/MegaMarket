package com.megamarket.picker.escaneo

sealed class CambioLectura {
    data class Codigo(val valor: String) : CambioLectura()
    data object Vacio : CambioLectura()
    data object Igual : CambioLectura()
}

class FiltroFotogramas {
    private var ultimo: String? = null
    private var iniciado = false

    fun observar(codigo: String?): CambioLectura = synchronized(this) {
        val valor = codigo?.takeIf { it.isNotEmpty() }
        if (iniciado && valor == ultimo) return CambioLectura.Igual
        iniciado = true
        ultimo = valor
        if (valor == null) CambioLectura.Vacio else CambioLectura.Codigo(valor)
    }
}
