package com.megamarket.app.data.remote.api

import com.megamarket.app.data.remote.dto.GuardarProductoRequest
import com.megamarket.app.data.remote.dto.ProductoRemotoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ProductoApi {
    @POST("api/productos")
    suspend fun crear(@Body body: GuardarProductoRequest): Response<ProductoRemotoResponse>

    @PUT("api/productos/{id}")
    suspend fun actualizar(
        @Path("id") remoteId: String,
        @Body body: GuardarProductoRequest
    ): Response<ProductoRemotoResponse>

    @DELETE("api/productos/{id}")
    suspend fun eliminar(@Path("id") remoteId: String): Response<Unit>
}
