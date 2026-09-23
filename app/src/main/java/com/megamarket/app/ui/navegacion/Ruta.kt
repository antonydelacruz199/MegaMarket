package com.megamarket.app.ui.navegacion

sealed class Ruta(val ruta: String) {
    data object Presentacion : Ruta("presentacion")
    data object Sesion : Ruta("sesion")
    data object PanelAdmin : Ruta("panel_admin")
    data object ProductosAdmin : Ruta("productos_admin")
    data object CrearProductoAdmin : Ruta("crear_producto_admin")
    data object EditarProductoAdmin : Ruta("editar_producto_admin")
}
