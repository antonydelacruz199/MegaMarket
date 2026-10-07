package com.megamarket.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Creación incluye [stock] inicial; actualización no envía stock (solo movimientos).
 */
data class GuardarProductoRequest(
    val clientUuid: String,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    @SerializedName("categoria_id") val categoriaId: String,
    @SerializedName("precio_centimos") val precioCentimos: Long,
    @SerializedName("descuento_porcentaje") val descuentoPorcentaje: Int,
    @SerializedName("es_oferta") val esOferta: Boolean,
    @SerializedName("imagen_key") val imagenKey: String,
    val activo: Boolean,
    val stock: Int? = null,
    val version: Long? = null
)
