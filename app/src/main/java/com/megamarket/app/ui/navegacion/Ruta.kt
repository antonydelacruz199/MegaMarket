package com.megamarket.app.ui.navegacion

sealed class Ruta(val ruta: String) {
    data object Presentacion : Ruta("presentacion")
    data object Sesion : Ruta("sesion")
    data object Inicio : Ruta("inicio")
    data object Catalogo : Ruta("catalogo")
    data object DetalleProducto : Ruta("detalle_producto")
    data object Favoritos : Ruta("favoritos")
    data object Carrito : Ruta("carrito")
    data object Compra : Ruta("compra")
    data object Confirmacion : Ruta("confirmacion")
    data object Perfil : Ruta("perfil")
    data object PanelAdmin : Ruta("panel_admin")
    data object ProductosAdmin : Ruta("productos_admin")
    data object CrearProductoAdmin : Ruta("crear_producto_admin")
    data object EditarProductoAdmin : Ruta("editar_producto_admin")
}

object DestinosInferioresCliente {
    val rutas = setOf(
        Ruta.Inicio.ruta,
        Ruta.Catalogo.ruta,
        Ruta.Favoritos.ruta,
        Ruta.Carrito.ruta
    )
}
