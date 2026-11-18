package com.megamarket.picker.data.local

/**
 * Sesión del aplicativo Picker/Repartidor.
 * Archivo distinto de megamarket_sesion_admin y megamarket_sesion_cliente.
 * No persiste la contraseña.
 */
interface AlmacenSesion {
    fun usuarioActivoId(): String?
    fun tokenActual(): String?
    fun tokenDe(usuarioId: String): String?
    fun perfil(): PerfilSesion?
    fun guardar(accessToken: String, perfil: PerfilSesion)
    fun limpiarSesion()
}

data class PerfilSesion(
    val id: String,
    val nombre: String,
    val usuario: String,
    val correo: String,
    val rol: String,
    val expiresAt: String?
)
