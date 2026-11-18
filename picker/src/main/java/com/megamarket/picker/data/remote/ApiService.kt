package com.megamarket.picker.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun me(@Header("X-Expected-User") owner: String? = null): Response<MeResponse>

    @GET("api/pedidos")
    suspend fun listarPedidos(
        @Query("estado") estado: String,
        @Header("X-Expected-User") owner: String
    ): Response<PedidosResponse>

    @GET("api/pedidos/{id}")
    suspend fun obtenerPedido(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<PedidoCompletoResponse>

    @PATCH("api/pedidos/{id}/estado")
    suspend fun transicionarPedido(
        @Path("id") id: String,
        @Body body: TransicionPedidoRequest,
        @Header("X-Expected-User") owner: String
    ): Response<PedidoCompletoResponse>

    @GET("api/picking/pedidos")
    suspend fun listarPicking(
        @Header("X-Expected-User") owner: String
    ): Response<PickingListaResponse>

    @GET("api/picking/pedidos/{id}")
    suspend fun obtenerPicking(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<PickingDetalleResponse>

    @POST("api/picking/pedidos/{id}/detalles/{detalleId}/escaneos")
    suspend fun registrarEscaneo(
        @Path("id") id: String,
        @Path("detalleId") detalleId: String,
        @Body body: EscaneoRequest,
        @Header("X-Expected-User") owner: String
    ): Response<EscaneoResponse>

    @POST("api/picking/pedidos/{id}/detalles/{detalleId}/faltante")
    suspend fun marcarFaltante(
        @Path("id") id: String,
        @Path("detalleId") detalleId: String,
        @Header("X-Expected-User") owner: String
    ): Response<FaltanteResponse>

    @POST("api/picking/pedidos/{id}/detalles/{detalleId}/eliminar")
    suspend fun eliminarLinea(
        @Path("id") id: String,
        @Path("detalleId") detalleId: String,
        @Header("X-Expected-User") owner: String
    ): Response<FaltanteResponse>

    @GET("api/picking/pedidos/{id}/detalles/{detalleId}/sustitutos")
    suspend fun listarSustitutos(
        @Path("id") id: String,
        @Path("detalleId") detalleId: String,
        @Header("X-Expected-User") owner: String
    ): Response<CandidatosSustitucionResponse>

    @POST("api/picking/pedidos/{id}/detalles/{detalleId}/sustituciones")
    suspend fun proponerSustitucion(
        @Path("id") id: String,
        @Path("detalleId") detalleId: String,
        @Body body: PropuestaSustitucionRequest,
        @Header("X-Expected-User") owner: String
    ): Response<SustitucionResponse>

    @GET("api/sustituciones/{id}")
    suspend fun obtenerSustitucion(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<SustitucionResponse>

    @GET("api/entregas/pedidos")
    suspend fun listarEntregas(
        @Header("X-Expected-User") owner: String
    ): Response<EntregasListaResponse>

    @GET("api/entregas/pedidos/{id}")
    suspend fun obtenerEntrega(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<EntregaDetalleResponse>

    @POST("api/entregas/pedidos/{id}")
    suspend fun registrarEntrega(
        @Path("id") id: String,
        @Body body: RegistrarEntregaRequest,
        @Header("X-Expected-User") owner: String
    ): Response<RegistrarEntregaResponse>

    @Multipart
    @POST("api/medios/entregas/{pedidoId}")
    suspend fun subirFotoEntrega(
        @Path("pedidoId") pedidoId: String,
        @Part archivo: MultipartBody.Part,
        @Header("X-Expected-User") owner: String
    ): Response<FotoEntregaResponse>
}
