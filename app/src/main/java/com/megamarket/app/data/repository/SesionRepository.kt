package com.megamarket.app.data.repository

import com.megamarket.app.data.fake.FakeData
import com.megamarket.app.data.local.Administrador
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.mapper.toModel
import com.megamarket.modelo.ClaveAcceso
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SesionRepository(
    private val administradorDao: AdministradorDao
) {
    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    suspend fun prepararCuentaLocal() = withContext(Dispatchers.IO) {
        val existente = administradorDao.buscarPorUsuario(FakeData.USUARIO)
        if (existente == null) {
            administradorDao.insertar(
                Administrador(
                    nombre = FakeData.NOMBRE,
                    usuario = FakeData.USUARIO,
                    correo = FakeData.CORREO,
                    claveHash = ClaveAcceso.generar(FakeData.CLAVE)
                )
            )
            return@withContext
        }
        if (esHashProvisional(existente.claveHash)) {
            administradorDao.actualizarAdministrador(existente.copy(claveHash = ClaveAcceso.generar(FakeData.CLAVE)))
        }
    }

    suspend fun iniciarSesion(nombreUsuario: String, clave: String): Usuario? = withContext(Dispatchers.IO) {
        val usuarioTrim = nombreUsuario.trim()
        if (usuarioTrim.isBlank() || clave.isBlank()) return@withContext null
        val cuenta = administradorDao.buscarPorUsuario(usuarioTrim) ?: return@withContext null
        if (!ClaveAcceso.coincide(clave, cuenta.claveHash)) return@withContext null
        cuenta.toModel().also { _usuario.value = it }
    }

    suspend fun cerrarSesion() {
        _usuario.value = null
    }

    private fun esHashProvisional(claveHash: String): Boolean =
        claveHash == "REMOTE" || !claveHash.contains(':')
}
