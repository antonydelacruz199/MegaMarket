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
    data object MisPedidos : Ruta("mis-pedidos")
    data object DetallePedido : Ruta("mis-pedidos/{${ARG_PEDIDO_REMOTO}}") {
        fun crear(pedidoId: String) = "mis-pedidos/$pedidoId"
    }
    data object Billetera : Ruta("billetera")
    data object PagoQr : Ruta("pago-qr/{${ARG_PEDIDO_REMOTO}}?$ARG_ABRIR_QR={$ARG_ABRIR_QR}") {
        fun crear(pedidoRemotoId: String, abrirQr: Boolean = false) =
            "pago-qr/$pedidoRemotoId?$ARG_ABRIR_QR=$abrirQr"
    }
    data object Sustituciones : Ruta("sustituciones")
    data object DetalleSustitucion : Ruta("sustituciones/{${ARG_SUSTITUCION}}") {
        fun crear(id: String) = "sustituciones/$id"
    }
    data object Detalle : Ruta("producto/{${DetalleProductoViewModel.ARG_PRODUCTO}}") {
        fun crear(id: Long) = "producto/$id"
    }

    companion object {
        const val ARG_SUSTITUCION = "sustitucionId"
        const val ARG_PEDIDO_REMOTO = "pedidoRemotoId"
        const val ARG_ABRIR_QR = "abrirQr"
    }
}
