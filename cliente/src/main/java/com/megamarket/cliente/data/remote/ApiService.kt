package com.megamarket.cliente.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Header

interface ApiService {
    @PUT("api/carritos/{id}")
    suspend fun reservarCarrito(@Path("id") id: String, @Body body: ReservaRequest, @Header("X-Expected-User") owner: String): Response<ReservaResponse>


    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/categorias")
    suspend fun listarCategorias(@Header("X-Expected-User") owner: String? = null): Response<CategoriasResponse>

    @GET("api/productos")
    suspend fun listarProductos(@Header("X-Expected-User") owner: String? = null): Response<ProductosResponse>

    @POST("api/pedidos")
    suspend fun crearPedido(@Body body: CrearPedidoRequest, @Header("X-Expected-User") owner: String? = null): Response<CrearPedidoResponse>
}
