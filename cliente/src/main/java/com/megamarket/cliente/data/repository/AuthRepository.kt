package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.ClienteDao
import com.megamarket.cliente.data.local.entities.ClienteEntity
import com.megamarket.cliente.data.mapper.toModel
import com.megamarket.modelo.ClaveAcceso
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AuthRepository(
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
            cuenta.toModel()
        }
        _usuario.value = cliente
        return cliente
    }

    fun cerrar() {
        _usuario.value = null
    }

    private suspend fun asegurarCuentaInicial() {
        if (dao.contar() > 0) return
        dao.insertar(
            ClienteEntity(
                nombre = "Cliente MegaMarket",
                usuario = "cliente",
                correo = "cliente@megamarket.com",
                claveHash = ClaveAcceso.generar("123456")
            )
        )
    }
}
