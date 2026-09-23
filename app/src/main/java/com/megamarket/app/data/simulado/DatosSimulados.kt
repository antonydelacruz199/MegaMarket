package com.megamarket.app.data.simulado

import com.megamarket.app.modelo.Producto
import com.megamarket.app.modelo.RolUsuario
import com.megamarket.app.modelo.Usuario

/**
 * Fuente temporal de datos. El catálogo completo se sembrará al conectar Room.
 */
object DatosSimulados {
    private data class CuentaDemo(
        val clave: String,
        val usuario: Usuario
    )

    private val cuentas = listOf(
        CuentaDemo(
            clave = "admin123",
            usuario = Usuario(
                id = 1,
                nombre = "Administrador Juan",
                correo = "admin",
                rol = RolUsuario.ADMINISTRADOR
            )
        )
    )

    val productos: List<Producto> = emptyList()

    fun buscarCuenta(correo: String, clave: String): Usuario? {
        return cuentas.firstOrNull { cuenta ->
            cuenta.usuario.correo.equals(correo.trim(), ignoreCase = true) &&
                cuenta.clave == clave
        }?.usuario
    }
}
