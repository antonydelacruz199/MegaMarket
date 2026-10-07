package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.entities.AdministradorEntity
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario

fun AdministradorEntity.toModel(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = RolUsuario.ADMINISTRADOR
)
