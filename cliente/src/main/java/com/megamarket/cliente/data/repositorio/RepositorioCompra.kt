package com.megamarket.cliente.data.repositorio

import com.megamarket.cliente.modelo.Direccion
import com.megamarket.cliente.modelo.LineaCarrito
import com.megamarket.cliente.modelo.ResumenCompra

class RepositorioCompra(
    private val carrito: RepositorioCarrito
) {
    private var ultimoResumen: ResumenCompra? = null

    fun ultimo(): ResumenCompra? = ultimoResumen

    suspend fun confirmar(lineas: List<LineaCarrito>, direccion: Direccion): String? {
        val errorDireccion = direccion.error()
        if (errorDireccion != null) return errorDireccion
        val comprables = lineas.filter { !it.producto.agotado && it.cantidad >= 1 }
        if (comprables.isEmpty()) return "El carrito no tiene productos disponibles"
        val total = comprables.sumOf { it.subtotalCentimos }
        ultimoResumen = ResumenCompra(
            cantidadProductos = comprables.sumOf { it.cantidad },
            totalCentimos = total,
            direccion = direccion
        )
        carrito.vaciar()
        return null
    }
}
