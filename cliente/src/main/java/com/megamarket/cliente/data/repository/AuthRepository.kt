package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.connectivity.ConnectivityObserver
import com.megamarket.cliente.data.local.dao.ClienteDao
import com.megamarket.cliente.data.local.entities.ClienteEntity
import com.megamarket.cliente.data.mapper.toModel
import com.megamarket.cliente.data.remote.api.AuthApi
import com.megamarket.cliente.data.remote.dto.LoginRequest
import com.megamarket.cliente.data.session.SessionStore
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Auth remota vía API. Offline: solo si ya hubo sesión válida previa.
 * No valida contraseña local como autoridad final.
 */
class AuthRepository(
    private val dao: ClienteDao,
    private val sessionStore: SessionStore,
    private val authApi: AuthApi? = null,
    private val connectivity: ConnectivityObserver? = null,
    private val trasLoginExitoso: (suspend () -> Unit)? = null
) {
    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    suspend fun autenticar(nombreUsuario: String, clave: String): Usuario? {
        val usuarioTrim = nombreUsuario.trim()
        if (usuarioTrim.isBlank() || clave.isBlank()) return null

        val conRed = connectivity?.hayRed() == true
        val resultado = withContext(Dispatchers.IO) {
            if (conRed && authApi != null) {
                autenticarRemoto(usuarioTrim, clave)
            } else {
                autenticarOffline(usuarioTrim)
            }
        }
        _usuario.value = resultado
        if (resultado != null) {
            trasLoginExitoso?.invoke()
        }
        return resultado
    }

    suspend fun cerrar() {
        withContext(Dispatchers.IO) {
            if (connectivity?.hayRed() == true && authApi != null && !sessionStore.accessToken.isNullOrBlank()) {
                runCatching { authApi.logout() }
            }
            sessionStore.cerrarSesionCompleta()
        }
        _usuario.value = null
    }

    private suspend fun autenticarRemoto(usuarioTrim: String, clave: String): Usuario? {
        val respuesta = try {
            authApi!!.login(
                LoginRequest(
                    usuario = usuarioTrim,
                    clave = clave,
                    deviceName = "MegaMarket Cliente Android"
                )
            )
        } catch (_: Exception) {
            return autenticarOffline(usuarioTrim)
        }

        if (!respuesta.isSuccessful) {
            return null
        }
        val cuerpo = respuesta.body() ?: return null
        if (!cuerpo.usuario.rol.equals(RolUsuario.CLIENTE.name, ignoreCase = true)) {
            return null
        }

        sessionStore.marcarSesionRemota(
            accessToken = cuerpo.accessToken,
            expiresAtEpochMs = parseExpires(cuerpo.expiresAt),
            remoteUserId = cuerpo.usuario.id,
            rol = RolUsuario.CLIENTE.name,
            usuario = cuerpo.usuario.usuario
        )

        val local = upsertPerfilLocal(
            nombre = cuerpo.usuario.nombre,
            usuario = cuerpo.usuario.usuario,
            correo = cuerpo.usuario.correo.orEmpty()
        )
        return local.toModel()
    }

    private suspend fun autenticarOffline(usuarioTrim: String): Usuario? {
        if (!sessionStore.autenticadoPreviamente) return null
        if (!sessionStore.usuarioLocal.equals(usuarioTrim, ignoreCase = true)) return null
        if (!sessionStore.rol.equals(RolUsuario.CLIENTE.name, ignoreCase = true)) return null
        // Política: si hay expiresAt y ya venció, no entrar offline.
        if (sessionStore.expiresAtEpochMs > 0L && !sessionStore.sesionRemotaValida()) return null
        val cuenta = dao.buscarPorUsuario(usuarioTrim) ?: return null
        return cuenta.toModel()
    }

    private suspend fun upsertPerfilLocal(nombre: String, usuario: String, correo: String): ClienteEntity {
        val existente = dao.buscarPorUsuario(usuario)
        return if (existente != null) {
            val actualizado = existente.copy(
                nombre = nombre,
                correo = correo.ifBlank { existente.correo },
                // No almacenar contraseña remota; marca de auth API.
                claveHash = existente.claveHash.ifBlank { "REMOTE" }
            )
            dao.actualizar(actualizado)
            actualizado
        } else {
            val nuevo = ClienteEntity(
                nombre = nombre,
                usuario = usuario,
                correo = correo.ifBlank { "$usuario@megamarket.local" },
                claveHash = "REMOTE"
            )
            val id = dao.insertar(nuevo)
            nuevo.copy(id = id)
        }
    }

    private fun parseExpires(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(iso)?.time
        } catch (_: Exception) {
            null
        }
    }
}
