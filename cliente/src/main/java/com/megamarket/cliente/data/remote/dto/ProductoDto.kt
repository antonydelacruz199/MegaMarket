package com.megamarket.cliente.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Contrato esperado de GET /api/productos (Neon vía API REST).
 * [id] es UUID remoto; nunca se usa como PK Room.
 */
data class ProductoDto(
    val id: String,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    @SerializedName("categoria_id") val categoriaId: String,
    @SerializedName("precio_centimos") val precioCentimos: Long,
    @SerializedName("precio_oferta_centimos") val precioOfertaCentimos: Long? = null,
    @SerializedName("descuento_porcentaje") val descuentoPorcentaje: Int = 0,
    val stock: Int,
    @SerializedName("imagen_key") val imagenKey: String,
    @SerializedName("es_oferta") val esOferta: Boolean,
    val activo: Boolean,
    val version: Long,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)
