package com.megamarket.cliente.modelo

data class ResumenCompra(
    val cantidadProductos: Int,
    val totalCentimos: Long,
    val direccion: Direccion
)
