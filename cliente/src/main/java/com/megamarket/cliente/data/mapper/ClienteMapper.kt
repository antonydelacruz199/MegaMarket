package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.Cliente
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario

fun Cliente.toModel(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = RolUsuario.CLIENTE
)
