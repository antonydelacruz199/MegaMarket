package com.megamarket.app.data.local

import android.content.Context

/**
 * Persistencia local de sesión remota (SharedPreferences).
 * No almacena contraseña en texto plano ni secretos del servidor.
 */
class SesionLocalStore(contexto: Context) {

    private val prefs = contexto.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var usuarioRemotoId: String?
        get() = prefs.getString(KEY_REMOTE_ID, null)
        set(value) = prefs.edit().putString(KEY_REMOTE_ID, value).apply()

    var usuario: String?
        get() = prefs.getString(KEY_USUARIO, null)
        set(value) = prefs.edit().putString(KEY_USUARIO, value).apply()

    var rol: String?
        get() = prefs.getString(KEY_ROL, null)
        set(value) = prefs.edit().putString(KEY_ROL, value).apply()

    var autenticadoEn: Long
        get() = prefs.getLong(KEY_AUTH_EN, 0L)
        set(value) = prefs.edit().putLong(KEY_AUTH_EN, value).apply()

    var expiresAt: String?
        get() = prefs.getString(KEY_EXPIRES, null)
        set(value) = prefs.edit().putString(KEY_EXPIRES, value).apply()

    var autenticacionRemotaPrevia: Boolean
        get() = prefs.getBoolean(KEY_REMOTA_PREVIA, false)
        set(value) = prefs.edit().putBoolean(KEY_REMOTA_PREVIA, value).apply()

    fun guardarSesionRemota(
        accessToken: String,
        usuarioRemotoId: String,
        usuario: String,
        rol: String,
        expiresAt: String?,
        autenticadoEn: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_REMOTE_ID, usuarioRemotoId)
            .putString(KEY_USUARIO, usuario)
            .putString(KEY_ROL, rol)
            .putString(KEY_EXPIRES, expiresAt)
            .putLong(KEY_AUTH_EN, autenticadoEn)
            .putBoolean(KEY_REMOTA_PREVIA, true)
            .apply()
    }

    fun limpiarToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun limpiarSesionCompleta() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "megamarket_sesion_admin"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_REMOTE_ID = "usuario_remoto_id"
        private const val KEY_USUARIO = "usuario"
        private const val KEY_ROL = "rol"
        private const val KEY_AUTH_EN = "autenticado_en"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_REMOTA_PREVIA = "autenticacion_remota_previa"
    }
}
