package com.megamarket.app.modelo

data class Producto(
    val id: Long,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long? = null,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean = true
)
