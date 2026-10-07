package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.ProductoDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ProductoApi {
    @GET("api/productos")
    suspend fun obtenerProductos(
        @Query("updatedSince") updatedSince: String? = null
    ): Response<List<ProductoDto>>
}
