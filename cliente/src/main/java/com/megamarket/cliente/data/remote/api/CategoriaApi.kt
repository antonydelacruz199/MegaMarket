package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.CategoriaDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * Orden de sync futuro: categorías antes que productos.
 * No se ejecuta sin backend real.
 */
interface CategoriaApi {
    @GET("api/categorias")
    suspend fun obtenerCategorias(): Response<List<CategoriaDto>>
}
