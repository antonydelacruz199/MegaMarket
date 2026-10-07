package com.megamarket.cliente.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sesión cifrada con Android Keystore (AES-GCM).
 * No guarda contraseñas ni secretos Neon.
 * Hasta existir backend, el login demo puede marcar [autenticadoPreviamente].
 */
class SessionStore(contexto: Context) {

    private val prefs: SharedPreferences =
        contexto.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var accessToken: String?
        get() = leerCifrado(KEY_ACCESS)
        set(value) = guardarCifrado(KEY_ACCESS, value)

    var refreshToken: String?
        get() = leerCifrado(KEY_REFRESH)
        set(value) = guardarCifrado(KEY_REFRESH, value)

    var remoteUserId: String?
        get() = prefs.getString(KEY_REMOTE_USER, null)
        set(value) = prefs.edit().putString(KEY_REMOTE_USER, value).apply()

    var rol: String?
        get() = prefs.getString(KEY_ROL, null)
        set(value) = prefs.edit().putString(KEY_ROL, value).apply()

    var fechaAutenticacion: Long
        get() = prefs.getLong(KEY_FECHA_AUTH, 0L)
        set(value) = prefs.edit().putLong(KEY_FECHA_AUTH, value).apply()

    var autenticadoPreviamente: Boolean
        get() = prefs.getBoolean(KEY_AUTH_PREVIA, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTH_PREVIA, value).apply()

    var expiresAtEpochMs: Long
        get() = prefs.getLong(KEY_EXPIRES, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES, value).apply()

    var usuarioLocal: String?
        get() = prefs.getString(KEY_USUARIO, null)
        set(value) = prefs.edit().putString(KEY_USUARIO, value).apply()

    fun sesionRemotaValida(ahora: Long = System.currentTimeMillis()): Boolean {
        val token = accessToken
        if (token.isNullOrBlank() || !autenticadoPreviamente) return false
        if (expiresAtEpochMs <= 0L) return true
        return ahora < expiresAtEpochMs
    }

    fun marcarSesionRemota(
        accessToken: String,
        expiresAtEpochMs: Long?,
        remoteUserId: String,
        rol: String,
        usuario: String
    ) {
        this.accessToken = accessToken
        this.expiresAtEpochMs = expiresAtEpochMs ?: 0L
        this.remoteUserId = remoteUserId
        this.rol = rol
        this.usuarioLocal = usuario
        fechaAutenticacion = System.currentTimeMillis()
        autenticadoPreviamente = true
    }

    fun marcarSesionInvalidaRemota() {
        limpiarTokens()
        expiresAtEpochMs = 0L
        // Conserva perfil local / autenticadoPreviamente para offline.
    }

    fun marcarSesionLocal(remoteUserId: String?, rol: String?) {
        this.remoteUserId = remoteUserId
        this.rol = rol
        fechaAutenticacion = System.currentTimeMillis()
        autenticadoPreviamente = true
    }

    fun limpiarTokens() {
        accessToken = null
        refreshToken = null
    }

    fun cerrarSesionCompleta() {
        limpiarTokens()
        remoteUserId = null
        rol = null
        usuarioLocal = null
        expiresAtEpochMs = 0L
        autenticadoPreviamente = false
    }

    private fun guardarCifrado(clave: String, valor: String?) {
        if (valor.isNullOrBlank()) {
            prefs.edit().remove(clave).remove(clave + SUF_IV).apply()
            return
        }
        val secret = obtenerOCrearClave()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val iv = cipher.iv
        val cifrado = cipher.doFinal(valor.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(clave, Base64.encodeToString(cifrado, Base64.NO_WRAP))
            .putString(clave + SUF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    private fun leerCifrado(clave: String): String? {
        val datos = prefs.getString(clave, null) ?: return null
        val ivB64 = prefs.getString(clave + SUF_IV, null) ?: return null
        return try {
            val secret = obtenerOCrearClave()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secret,
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(datos, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun obtenerOCrearClave(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existente = ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existente != null) return existente.secretKey
        val gen = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    companion object {
        private const val PREFS = "megamarket_cliente_session"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_REMOTE_USER = "remote_user"
        private const val KEY_ROL = "rol"
        private const val KEY_FECHA_AUTH = "fecha_auth"
        private const val KEY_AUTH_PREVIA = "auth_previa"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_USUARIO = "usuario_local"
        private const val SUF_IV = "_iv"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "megamarket_cliente_session_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
