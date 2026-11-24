package com.megamarket.picker.ui.navigation

import com.megamarket.picker.model.RolOperativo

sealed class Ruta(val ruta: String) {
    data object Sesion : Ruta("sesion")

    data object PickerDisponibles : Ruta("picker/disponibles")
    data object PickerPreparacion : Ruta("picker/preparacion")
    data object PickerDespacho : Ruta("picker/despacho")

    /** Compat: inicio picker = disponibles. */
    data object Picker : Ruta(PickerDisponibles.ruta)

    data object RepartidorEnCamino : Ruta("repartidor/en_camino")
    data object RepartidorListos : Ruta("repartidor/listos")
    data object RepartidorEntregados : Ruta("repartidor/entregados")

    /** Compat: inicio repartidor = en camino. */
    data object Repartidor : Ruta(RepartidorEnCamino.ruta)

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
        fun crear(pedidoId: String, detalleId: String) =
            "preparacion/$pedidoId/sustitucion/$detalleId"
    }

    companion object {
        const val ARG_PEDIDO = "pedidoId"
        const val ARG_DETALLE = "detalleId"
        const val REFRESCAR_PREPARACION = "refrescarPreparacion"

        val destinosPicker = listOf(
            PickerDisponibles.ruta,
            PickerPreparacion.ruta,
            PickerDespacho.ruta
        )

        val destinosRepartidor = listOf(
            RepartidorEnCamino.ruta,
            RepartidorListos.ruta,
            RepartidorEntregados.ruta
        )
    }
}

enum class SeccionPicker {
    DISPONIBLES,
    PREPARACION,
    DESPACHO
}

enum class SeccionReparto(val estadoApi: String) {
    EN_CAMINO("EN_CAMINO"),
    LISTOS("LISTO"),
    ENTREGADOS("ENTREGADO")
}

fun rutaDeRol(rol: RolOperativo): String = when (rol) {
    RolOperativo.PICKER -> Ruta.PickerDisponibles.ruta
    RolOperativo.REPARTIDOR -> Ruta.RepartidorEnCamino.ruta
}

fun seccionPickerDeRuta(ruta: String): SeccionPicker = when (ruta) {
    Ruta.PickerPreparacion.ruta -> SeccionPicker.PREPARACION
    Ruta.PickerDespacho.ruta -> SeccionPicker.DESPACHO
    else -> SeccionPicker.DISPONIBLES
}

fun seccionRepartoDeRuta(ruta: String): SeccionReparto = when (ruta) {
    Ruta.RepartidorListos.ruta -> SeccionReparto.LISTOS
    Ruta.RepartidorEntregados.ruta -> SeccionReparto.ENTREGADOS
    else -> SeccionReparto.EN_CAMINO
}
