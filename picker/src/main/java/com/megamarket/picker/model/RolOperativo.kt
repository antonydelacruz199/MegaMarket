package com.megamarket.picker.model

enum class RolOperativo {
    PICKER,
    REPARTIDOR;

    companion object {
        fun desde(valor: String?): RolOperativo? {
            val texto = valor?.trim().orEmpty()
            if (texto.isEmpty()) return null
            return entries.firstOrNull { it.name.equals(texto, ignoreCase = true) }
        }
    }
}

data class UsuarioOperativo(
    val id: String,
    val nombre: String,
    val usuario: String,
    val correo: String,
    val rol: RolOperativo
)
