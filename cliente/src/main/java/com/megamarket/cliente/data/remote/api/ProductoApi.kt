package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.ProductoDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * Contrato preparado. No se invoca automáticamente mientras API_BASE_URL esté vacía.
 */
interface ProductoApi {
    @GET("api/productos")
    suspend fun obtenerProductos(): Response<List<ProductoDto>>
}
