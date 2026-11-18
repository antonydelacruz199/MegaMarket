package com.megamarket.picker.ui.navigation

import com.megamarket.picker.model.RolOperativo

sealed class Ruta(val ruta: String) {
    data object Sesion : Ruta("sesion")
    data object Picker : Ruta("picker")
    data object Repartidor : Ruta("repartidor")
    data object Entrega : Ruta("repartidor/entrega/{$ARG_PEDIDO}") {
        fun crear(id: String) = "repartidor/entrega/$id"
    }
    data object Preparacion : Ruta("preparacion/{$ARG_PEDIDO}") {
        fun crear(id: String) = "preparacion/$id"
    }

    data object Escaner : Ruta("preparacion/{$ARG_PEDIDO}/escaner/{$ARG_DETALLE}") {
        fun crear(pedidoId: String, detalleId: String) = "preparacion/$pedidoId/escaner/$detalleId"
    }

    data object Sustitucion : Ruta("preparacion/{$ARG_PEDIDO}/sustitucion/{$ARG_DETALLE}") {
        fun crear(pedidoId: String, detalleId: String) = "preparacion/$pedidoId/sustitucion/$detalleId"
    }

    companion object {
        const val ARG_PEDIDO = "pedidoId"
        const val ARG_DETALLE = "detalleId"
        const val REFRESCAR_PREPARACION = "refrescarPreparacion"
    }
}

fun rutaDeRol(rol: RolOperativo): String = when (rol) {
    RolOperativo.PICKER -> Ruta.Picker.ruta
    RolOperativo.REPARTIDOR -> Ruta.Repartidor.ruta
}
