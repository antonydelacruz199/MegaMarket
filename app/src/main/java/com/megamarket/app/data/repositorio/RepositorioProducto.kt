package com.megamarket.app.data.repositorio

import com.megamarket.app.data.simulado.DatosSimulados
import com.megamarket.app.modelo.Producto

class RepositorioProducto(
    private val datos: DatosSimulados = DatosSimulados
) {
    fun obtenerActivos(): List<Producto> {
        return datos.productos.filter { it.activo }
    }
}
