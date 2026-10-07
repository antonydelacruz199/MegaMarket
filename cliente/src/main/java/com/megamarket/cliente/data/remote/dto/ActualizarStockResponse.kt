package com.megamarket.cliente.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ActualizarStockResponse(
    val id: String,
    val stock: Int,
    @SerializedName("updated_at") val updatedAt: String? = null
)
