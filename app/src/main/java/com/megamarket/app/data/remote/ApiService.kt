package com.megamarket.app.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/categorias")
    suspend fun listarCategorias(): Response<CategoriasResponse>

    @GET("api/productos")
    suspend fun listarProductos(): Response<ProductosResponse>

    @GET("api/productos/{id}")
    suspend fun obtenerProducto(@Path("id") id: String): Response<ProductoResponse>

    @POST("api/productos")
    suspend fun crearProducto(@Body body: CrearProductoRequest): Response<ProductoResponse>

    @PATCH("api/productos/{id}")
    suspend fun actualizarProducto(
        @Path("id") id: String,
        @Body body: ActualizarProductoRequest
    ): Response<ProductoResponse>

    @HTTP(method = "DELETE", path = "api/productos/{id}", hasBody = true)
    suspend fun eliminarProducto(
        @Path("id") id: String,
        @Body body: EliminarProductoRequest
    ): Response<EliminarProductoResponse>

    @POST("api/inventario/movimientos")
    suspend fun registrarMovimiento(
        @Body body: MovimientoInventarioRequest
    ): Response<MovimientoInventarioResponse>

    @Multipart
    @POST("api/medios/productos/{productoId}")
    suspend fun subirImagenProducto(
        @Path("productoId") productoId: String,
        @Part archivo: MultipartBody.Part
    ): Response<SubidaImagenProductoResponse>
}
