package com.megamarket.modelo

data class Usuario(
    val id: Long,
    val nombre: String,
    val usuario: String,
    val correo: String,
    val rol: RolUsuario
)
