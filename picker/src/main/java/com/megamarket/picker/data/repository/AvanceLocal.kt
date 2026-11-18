package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.OperacionPicking
import com.megamarket.picker.data.local.OperacionPickingEntity

data class ResumenOperacionLocal(
    val uuidOperacion: String,
    val operacion: String,
    val estado: String,
    val resultado: String?,
    val codigoBarras: String?,
    val cantidad: Int,
    val ultimoError: String?
)

fun OperacionPickingEntity.aResumen(): ResumenOperacionLocal = ResumenOperacionLocal(
    uuidOperacion = uuidOperacion,
    operacion = operacion,
    estado = estado,
    resultado = resultado,
    codigoBarras = codigoBarras,
    cantidad = cantidad,
    ultimoError = ultimoError
)

fun cantidadPendienteEscaneos(ops: List<OperacionPickingEntity>): Int =
    ops.filter {
        it.operacion == OperacionPicking.ESCANEAR && it.estado in OperacionPicking.EN_VUELO
    }.sumOf { it.cantidad }

fun hayFaltantePendiente(ops: List<OperacionPickingEntity>): Boolean =
    ops.any {
        it.operacion == OperacionPicking.MARCAR_FALTANTE && it.estado in OperacionPicking.EN_VUELO
    }

fun cabeEscaneo(armadaConfirmada: Int, pendienteLocal: Int, solicitada: Int, cantidad: Int): Boolean =
    armadaConfirmada + pendienteLocal + cantidad <= solicitada
