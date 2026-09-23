package com.megamarket.app.data.repositorio

import com.megamarket.app.data.simulado.DatosSimulados
import com.megamarket.app.modelo.Usuario

class RepositorioSesion(
    private val datos: DatosSimulados = DatosSimulados
) {
    fun autenticar(correo: String, clave: String): Usuario? {
        if (correo.isBlank() || clave.isBlank()) return null
        return datos.buscarCuenta(correo, clave)
    }
}
