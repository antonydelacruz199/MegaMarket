package com.megamarket.cliente.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CategoriaDto(
    val id: String,
    val nombre: String,
    val version: Long,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)
