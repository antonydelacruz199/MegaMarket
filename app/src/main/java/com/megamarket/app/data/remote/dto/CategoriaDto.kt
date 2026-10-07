package com.megamarket.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CategoriaDto(
    val id: String,
    val nombre: String,
    val version: Long,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)
