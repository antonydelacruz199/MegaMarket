package com.megamarket.picker.data.local

import android.content.Context

internal const val NOMBRE_PREFERENCIAS_SESION = "megamarket_sesion_picker"

class SesionLocalStore(contexto: Context) : AlmacenSesion {

    private val prefs = contexto.applicationContext
        .getSharedPreferences(NOMBRE_PREFERENCIAS_SESION, Context.MODE_PRIVATE)

    override fun usuarioActivoId(): String? {
        if (tokenActual().isNullOrBlank()) return null
        return prefs.getString(KEY_REMOTE_ID, null)?.takeIf { it.isNotBlank() }
    }

    override fun tokenActual(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun tokenDe(usuarioId: String): String? =
        tokenActual()?.takeIf { usuarioActivoId() == usuarioId }

    override fun perfil(): PerfilSesion? {
        if (tokenActual().isNullOrBlank()) return null
        val id = prefs.getString(KEY_REMOTE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val rol = prefs.getString(KEY_ROL, null)?.takeIf { it.isNotBlank() } ?: return null
        return PerfilSesion(
            id = id,
            nombre = prefs.getString(KEY_NOMBRE, null).orEmpty(),
            usuario = prefs.getString(KEY_USUARIO, null).orEmpty(),
            correo = prefs.getString(KEY_CORREO, null).orEmpty(),
            rol = rol,
            expiresAt = prefs.getString(KEY_EXPIRES, null)
        )
    }

    override fun guardar(accessToken: String, perfil: PerfilSesion) {
        prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_REMOTE_ID, perfil.id)
            .putString(KEY_NOMBRE, perfil.nombre)
            .putString(KEY_USUARIO, perfil.usuario)
            .putString(KEY_CORREO, perfil.correo)
            .putString(KEY_ROL, perfil.rol)
            .putString(KEY_EXPIRES, perfil.expiresAt)
            .putLong(KEY_AUTH_EN, System.currentTimeMillis())
            .commit()
    }

    override fun limpiarSesion() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REMOTE_ID)
            .remove(KEY_NOMBRE)
            .remove(KEY_USUARIO)
            .remove(KEY_CORREO)
            .remove(KEY_ROL)
            .remove(KEY_EXPIRES)
            .remove(KEY_AUTH_EN)
            .commit()
    }

    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_REMOTE_ID = "usuario_remoto_id"
        private const val KEY_NOMBRE = "nombre"
        private const val KEY_USUARIO = "usuario"
        private const val KEY_CORREO = "correo"
        private const val KEY_ROL = "rol"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_AUTH_EN = "autenticado_en"
    }
}
