package com.megamarket.cliente.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/categorias")
    suspend fun listarCategorias(): Response<CategoriasResponse>

    @GET("api/productos")
    suspend fun listarProductos(): Response<ProductosResponse>

    @POST("api/pedidos")
    suspend fun crearPedido(@Body body: CrearPedidoRequest): Response<CrearPedidoResponse>
}
