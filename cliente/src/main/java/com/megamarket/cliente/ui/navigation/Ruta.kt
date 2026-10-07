package com.megamarket.cliente.ui.navigation

import com.megamarket.cliente.viewmodel.ConfirmacionViewModel
import com.megamarket.cliente.viewmodel.DetalleProductoViewModel

sealed class Ruta(val ruta: String) {
    data object Presentacion : Ruta("presentacion")
    data object Sesion : Ruta("sesion")
    data object Inicio : Ruta("inicio")
    data object Catalogo : Ruta("catalogo")
    data object Ofertas : Ruta("ofertas")
    data object Favoritos : Ruta("favoritos")
    data object Carrito : Ruta("carrito")
    data object Checkout : Ruta("checkout")
    data object Confirmacion : Ruta("confirmacion/{${ConfirmacionViewModel.ARG_PEDIDO}}") {
        fun crear(pedidoId: Long) = "confirmacion/$pedidoId"
    }
    data object Perfil : Ruta("perfil")
    data object Detalle : Ruta("producto/{${DetalleProductoViewModel.ARG_PRODUCTO}}") {
        fun crear(id: Long) = "producto/$id"
    }
}
