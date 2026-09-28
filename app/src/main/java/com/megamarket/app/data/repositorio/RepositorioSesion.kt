package com.megamarket.app.data.repositorio

import com.megamarket.app.data.local.EntidadAdministrador
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.modelo.ClaveAcceso
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepositorioSesion(
    private val dao: AdministradorDao
) {
    suspend fun autenticar(nombreUsuario: String, clave: String): Usuario? = withContext(Dispatchers.IO) {
        if (nombreUsuario.isBlank() || clave.isBlank()) return@withContext null
        asegurarCuentaInicial()
        val cuenta = dao.buscarPorUsuario(nombreUsuario.trim()) ?: return@withContext null
        if (!ClaveAcceso.coincide(clave, cuenta.claveHash)) return@withContext null
        cuenta.aUsuario()
    }

    suspend fun registrar(nombre: String, usuario: String, correo: String, clave: String): Usuario? =
        withContext(Dispatchers.IO) {
            val nombreUsuario = usuario.trim()
            if (nombre.isBlank() || nombreUsuario.isBlank() || correo.isBlank() || clave.isBlank()) {
                return@withContext null
            }
            if (dao.buscarPorUsuario(nombreUsuario) != null) return@withContext null
            val id = dao.insertar(
                EntidadAdministrador(
                    nombre = nombre.trim(),
                    usuario = nombreUsuario,
                    correo = correo.trim(),
                    claveHash = ClaveAcceso.generar(clave)
                )
            )
            Usuario(
                id = id,
                nombre = nombre.trim(),
                usuario = nombreUsuario,
                correo = correo.trim(),
                rol = RolUsuario.ADMINISTRADOR
            )
        }

    private suspend fun asegurarCuentaInicial() {
        if (dao.contar() > 0) return
        dao.insertar(
            EntidadAdministrador(
                nombre = "Administrador Juan",
                usuario = "admin",
                correo = "admin@megamarket.com",
                claveHash = ClaveAcceso.generar("admin123")
            )
        )
    }
}

private fun EntidadAdministrador.aUsuario(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = RolUsuario.ADMINISTRADOR
)
