package com.megamarket.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ProductoRemotoResponse(
    val id: String,
    val version: Long? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)
