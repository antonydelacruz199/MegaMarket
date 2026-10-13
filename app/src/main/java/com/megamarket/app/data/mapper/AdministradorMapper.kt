package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.Administrador
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario

fun Administrador.toModel(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = RolUsuario.ADMINISTRADOR
)
