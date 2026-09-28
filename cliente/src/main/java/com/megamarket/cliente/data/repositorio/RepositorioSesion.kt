package com.megamarket.cliente.data.repositorio

import com.megamarket.cliente.data.local.ClienteDao
import com.megamarket.cliente.data.local.EntidadCliente
import com.megamarket.modelo.ClaveAcceso
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RepositorioSesion(
    private val dao: ClienteDao
) {
    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    suspend fun autenticar(nombreUsuario: String, clave: String): Usuario? {
        val cliente = withContext(Dispatchers.IO) {
            if (nombreUsuario.isBlank() || clave.isBlank()) return@withContext null
            asegurarCuentaInicial()
            val cuenta = dao.buscarPorUsuario(nombreUsuario.trim()) ?: return@withContext null
            if (!ClaveAcceso.coincide(clave, cuenta.claveHash)) return@withContext null
            cuenta.aUsuario()
        }
        _usuario.value = cliente
        return cliente
    }

    suspend fun registrar(nombre: String, usuario: String, correo: String, clave: String): Usuario? =
        withContext(Dispatchers.IO) {
            val nombreUsuario = usuario.trim()
            if (nombre.isBlank() || nombreUsuario.isBlank() || correo.isBlank() || clave.isBlank()) {
                return@withContext null
            }
            if (dao.buscarPorUsuario(nombreUsuario) != null) return@withContext null
            val id = dao.insertar(
                EntidadCliente(
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
                rol = RolUsuario.CLIENTE
            )
        }

    fun cerrar() {
        _usuario.value = null
    }

    private suspend fun asegurarCuentaInicial() {
        if (dao.contar() > 0) return
        dao.insertar(
            EntidadCliente(
                nombre = "Cliente MegaMarket",
                usuario = "cliente",
                correo = "cliente@megamarket.com",
                claveHash = ClaveAcceso.generar("123456")
            )
        )
    }
}

private fun EntidadCliente.aUsuario(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = RolUsuario.CLIENTE
)
