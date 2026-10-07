package com.megamarket.app.data.repository

import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.local.entities.AdministradorEntity
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.ClaveAcceso
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val dao: AdministradorDao
) {
    suspend fun autenticar(nombreUsuario: String, clave: String): Usuario? = withContext(Dispatchers.IO) {
        if (nombreUsuario.isBlank() || clave.isBlank()) return@withContext null
        asegurarCuentaInicial()
        val cuenta = dao.buscarPorUsuario(nombreUsuario.trim()) ?: return@withContext null
        if (!ClaveAcceso.coincide(clave, cuenta.claveHash)) return@withContext null
        cuenta.toModel()
    }

    private suspend fun asegurarCuentaInicial() {
        if (dao.contar() > 0) return
        dao.insertar(
            AdministradorEntity(
                nombre = "Administrador Juan",
                usuario = "admin",
                correo = "admin@megamarket.com",
                claveHash = ClaveAcceso.generar("admin123")
            )
        )
    }
}
