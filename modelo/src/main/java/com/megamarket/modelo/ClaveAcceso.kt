package com.megamarket.modelo

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Guarda y comprueba contraseñas sin conservar el texto original.
 * El registro tiene la forma sal:hash, ambos en Base64.
 */
object ClaveAcceso {
    private const val ITERACIONES = 12_000
    private const val BITS = 256
    private const val SEPARADOR = ":"

    fun generar(clave: String): String {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derivar(clave, sal)
        return codificar(sal) + SEPARADOR + codificar(hash)
    }

    fun coincide(clave: String, registro: String): Boolean {
        val partes = registro.split(SEPARADOR)
        if (partes.size != 2) return false
        return try {
            val sal = Base64.decode(partes[0], Base64.NO_WRAP)
            val esperado = Base64.decode(partes[1], Base64.NO_WRAP)
            MessageDigest.isEqual(derivar(clave, sal), esperado)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derivar(clave: String, sal: ByteArray): ByteArray {
        val especificacion = PBEKeySpec(clave.toCharArray(), sal, ITERACIONES, BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(especificacion)
                .encoded
        } finally {
            especificacion.clearPassword()
        }
    }

    private fun codificar(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}
