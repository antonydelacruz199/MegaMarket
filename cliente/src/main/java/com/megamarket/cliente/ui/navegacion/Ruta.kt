package com.megamarket.cliente.ui.navegacion

import com.megamarket.cliente.viewmodel.ViewModelDetalle

sealed class Ruta(val ruta: String) {
    data object Presentacion : Ruta("presentacion")
    data object Sesion : Ruta("sesion")
    data object Inicio : Ruta("inicio")
    data object Catalogo : Ruta("catalogo")
    data object Ofertas : Ruta("ofertas")
    data object Favoritos : Ruta("favoritos")
    data object Carrito : Ruta("carrito")
    data object Compra : Ruta("compra")
    data object Confirmacion : Ruta("confirmacion")
    data object Perfil : Ruta("perfil")
    data object Detalle : Ruta("producto/{${ViewModelDetalle.ARG_PRODUCTO}}") {
        fun crear(id: Long) = "producto/$id"
    }
}
