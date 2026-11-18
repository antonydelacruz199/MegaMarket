package com.megamarket.picker.camera

class VinculoCamara(
    private val desenlazar: () -> Unit,
    private val cerrarLector: () -> Unit,
    private val cerrarEjecutor: () -> Unit
) {
    var cerrado: Boolean = false
        private set
    var abierto: Boolean = false
        private set

    fun marcarAbierto(): Boolean {
        if (cerrado) return false
        abierto = true
        return true
    }

    fun cerrar() {
        if (cerrado) return
        cerrado = true
        abierto = false
        runCatching { desenlazar() }
        runCatching { cerrarLector() }
        runCatching { cerrarEjecutor() }
    }
}
