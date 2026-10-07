package com.megamarket.app.data.remote.api

import com.megamarket.app.data.remote.dto.CategoriaDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CategoriaApi {
    @GET("api/categorias")
    suspend fun obtenerCategorias(
        @Query("updatedSince") updatedSince: String? = null
    ): Response<List<CategoriaDto>>
}
