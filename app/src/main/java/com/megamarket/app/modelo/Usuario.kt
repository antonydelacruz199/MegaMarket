package com.megamarket.app.modelo

data class Usuario(
    val id: Long,
    val nombre: String,
    val correo: String,
    val rol: RolUsuario
)
