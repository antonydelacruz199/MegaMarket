package com.megamarket.picker.data.repository

fun urlImagenProducto(baseUrl: String, productoId: String): String? {
    val id = productoId.trim()
    if (id.isEmpty()) return null
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return "${base}api/medios/productos/$id?download=1"
}

fun agruparPorPasillo(lineas: List<LineaPreparacion>): List<GrupoPasillo> {
    val orden = linkedMapOf<String, MutableList<LineaPreparacion>>()
    for (linea in lineas) {
        val nombre = linea.pasillo?.trim()?.takeIf { it.isNotEmpty() } ?: SIN_PASILLO
        orden.getOrPut(nombre) { mutableListOf() }.add(linea)
    }
    return orden.map { (pasillo, items) -> GrupoPasillo(pasillo, items.toList()) }
}

const val SIN_PASILLO = "Sin pasillo"
