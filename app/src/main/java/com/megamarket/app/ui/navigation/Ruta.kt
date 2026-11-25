package com.megamarket.app.ui.navigation

import com.megamarket.app.viewmodel.FormularioProductoViewModel

sealed class Ruta(val ruta: String) {
    data object Presentacion : Ruta("presentacion")
    data object Sesion : Ruta("sesion")
    data object PanelAdmin : Ruta("panel_admin")
    data object ProductosAdmin : Ruta("productos_admin")
    data object BilleteraAdmin : Ruta("billetera_admin")
    data object CrearProductoAdmin : Ruta("crear_producto_admin")
    data object EditarProductoAdmin :
        Ruta("editar_producto_admin/{${FormularioProductoViewModel.ARG_PRODUCTO_ID}}") {
        fun crear(id: Long) = "editar_producto_admin/$id"
    }

    // ---------- Fase 7: abastecimiento (proveedores y órdenes de compra) ----------

    data object ProveedoresAdmin : Ruta("proveedores_admin")
    data object NuevoProveedorAdmin : Ruta("nuevo_proveedor_admin")
    data object EditarProveedorAdmin : Ruta("editar_proveedor_admin/{$ARG_PROVEEDOR_ID}") {
        fun crear(id: String) = "editar_proveedor_admin/$id"
    }
    data object DetalleProveedorAdmin : Ruta("detalle_proveedor_admin/{$ARG_PROVEEDOR_ID}") {
        fun crear(id: String) = "detalle_proveedor_admin/$id"
    }

    data object OrdenesCompraAdmin : Ruta("ordenes_compra_admin")
    data object NuevaOrdenCompraAdmin :
        Ruta("nueva_orden_compra_admin?$ARG_PROVEEDOR_ID={$ARG_PROVEEDOR_ID}") {
        /** Con `proveedorId` la orden nace con ese proveedor preseleccionado. */
        fun crear(proveedorId: String? = null): String =
            "nueva_orden_compra_admin?$ARG_PROVEEDOR_ID=${proveedorId.orEmpty()}"
    }
    data object DetalleOrdenCompraAdmin : Ruta("detalle_orden_compra_admin/{$ARG_ORDEN_ID}") {
        fun crear(id: String) = "detalle_orden_compra_admin/$id"
    }

    companion object {
        const val ARG_PROVEEDOR_ID = "proveedorId"
        const val ARG_ORDEN_ID = "ordenId"
    }
}
