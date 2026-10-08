package com.megamarket.app.data.repository

import com.megamarket.app.data.connectivity.ConnectivityObserver
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.local.entities.AdministradorEntity
import com.megamarket.app.data.mapper.toModel
import com.megamarket.app.data.remote.api.AuthApi
import com.megamarket.app.data.remote.dto.LoginRequest
import com.megamarket.app.data.session.SessionStore
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AuthRepository(
    private val dao: AdministradorDao,
    private val sessionStore: SessionStore,
    private val authApi: AuthApi? = null,
    private val connectivity: ConnectivityObserver? = null,
    private val trasLoginExitoso: (suspend () -> Unit)? = null
) {
    suspend fun autenticar(nombreUsuario: String, clave: String): Usuario? = withContext(Dispatchers.IO) {
        val usuarioTrim = nombreUsuario.trim()
        if (usuarioTrim.isBlank() || clave.isBlank()) return@withContext null

        val conRed = connectivity?.hayRed() == true
        val usuario = if (conRed && authApi != null) {
            autenticarRemoto(usuarioTrim, clave)
        } else {
            autenticarOffline(usuarioTrim)
        }
        if (usuario != null) {
            runCatching { trasLoginExitoso?.invoke() }
        }
        usuario
    }

    suspend fun cerrar() = withContext(Dispatchers.IO) {
        if (connectivity?.hayRed() == true && authApi != null && !sessionStore.accessToken.isNullOrBlank()) {
            runCatching { authApi.logout() }
        }
        sessionStore.cerrarSesionCompleta()
    }

    private suspend fun autenticarRemoto(usuarioTrim: String, clave: String): Usuario? {
        val respuesta = try {
            authApi!!.login(
                LoginRequest(
                    usuario = usuarioTrim,
                    clave = clave,
                    deviceName = "MegaMarket Admin Android"
                )
            )
        } catch (_: Exception) {
            return autenticarOffline(usuarioTrim)
        }
        if (!respuesta.isSuccessful) return null
        val cuerpo = respuesta.body() ?: return null
        if (!cuerpo.usuario.rol.equals(RolUsuario.ADMINISTRADOR.name, ignoreCase = true)) {
            return null
        }
        sessionStore.marcarSesionRemota(
            accessToken = cuerpo.accessToken,
            expiresAtEpochMs = parseExpires(cuerpo.expiresAt),
            remoteUserId = cuerpo.usuario.id,
            rol = RolUsuario.ADMINISTRADOR.name,
            usuario = cuerpo.usuario.usuario
        )
        return upsertPerfilLocal(
            nombre = cuerpo.usuario.nombre,
            usuario = cuerpo.usuario.usuario,
            correo = cuerpo.usuario.correo.orEmpty()
        ).toModel()
    }

    private suspend fun autenticarOffline(usuarioTrim: String): Usuario? {
        if (!sessionStore.autenticadoPreviamente) return null
        if (!sessionStore.usuarioLocal.equals(usuarioTrim, ignoreCase = true)) return null
        if (!sessionStore.rol.equals(RolUsuario.ADMINISTRADOR.name, ignoreCase = true)) return null
        return dao.buscarPorUsuario(usuarioTrim)?.toModel()
    }

    private suspend fun upsertPerfilLocal(
        nombre: String,
        usuario: String,
        correo: String
    ): AdministradorEntity {
        val existente = dao.buscarPorUsuario(usuario)
        return if (existente != null) {
            val actualizado = existente.copy(
                nombre = nombre,
                correo = correo.ifBlank { existente.correo },
                claveHash = existente.claveHash.ifBlank { "REMOTE" }
            )
            dao.actualizar(actualizado)
            actualizado
        } else {
            val nuevo = AdministradorEntity(
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
