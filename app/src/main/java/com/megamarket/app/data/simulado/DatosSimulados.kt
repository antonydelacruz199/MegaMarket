package com.megamarket.app.data.simulado

import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario

/**
 * Fuente temporal de datos. El catálogo completo se sembrará al conectar Room.
 */
object DatosSimulados {
    private data class CuentaAdministrador(
        val clave: String,
        val usuario: Usuario
    )

    private val cuentas = listOf(
        CuentaDemo(
            clave = "admin123",
            usuario = Usuario(
                id = 1,
                nombre = "Administ  rador Juan",
                usuario = "admin",
                correo = "admin@megamarket.com",
                rol = RolUsuario.ADMINISTRADOR
            )
        )
    )

    fun buscarCuenta(usuario: String, clave: String): Usuario? {
        val buscado = usuario.trim()
        return cuentas.firstOrNull { cuenta ->
            cuenta.usuario.usuario.equals(buscado, ignoreCase = true) &&
                cuenta.clave == clave
        }?.usuario
    }
}
