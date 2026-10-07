package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.entities.ClienteEntity
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario

fun ClienteEntity.toModel(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = RolUsuario.CLIENTE
)
