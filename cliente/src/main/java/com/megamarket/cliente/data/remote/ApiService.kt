package com.megamarket.cliente.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @PUT("api/carritos/{id}")
    suspend fun reservarCarrito(@Path("id") id: String, @Body body: ReservaRequest, @Header("X-Expected-User") owner: String): Response<ReservaResponse>


    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/categorias")
    suspend fun listarCategorias(@Header("X-Expected-User") owner: String? = null): Response<CategoriasResponse>

    @GET("api/productos")
    suspend fun listarProductos(
        @Header("X-Expected-User") owner: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("q") q: String? = null
    ): Response<ProductosResponse>

    @GET("api/franjas")
    suspend fun listarFranjas(
        @Header("X-Expected-User") owner: String? = null
    ): Response<ListaFranjasResponse>

    @GET("api/franjas/{id}")
    suspend fun obtenerFranja(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String? = null
    ): Response<FranjaResponse>

    @POST("api/pedidos")
    suspend fun crearPedido(@Body body: CrearPedidoRequest, @Header("X-Expected-User") owner: String? = null): Response<CrearPedidoResponse>

    @GET("api/pedidos")
    suspend fun listarPedidos(
        @Query("estado") estado: String? = null,
        @Header("X-Expected-User") owner: String,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("q") q: String? = null,
        @Query("filtro") filtro: String? = null,
        @Query("estados") estados: String? = null
    ): Response<ListaPedidosResponse>

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

    @GET("api/sustituciones")
    suspend fun listarSustituciones(
        @Query("estado") estado: String? = null,
        @Header("X-Expected-User") owner: String,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ListaSustitucionesResponse>

    @GET("api/sustituciones/{id}")
    suspend fun obtenerSustitucion(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<SustitucionDecisionResponse>

    @POST("api/sustituciones/{id}/aceptar")
    suspend fun aceptarSustitucion(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<SustitucionDecisionResponse>

    @POST("api/sustituciones/{id}/rechazar")
    suspend fun rechazarSustitucion(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<SustitucionDecisionResponse>

    @GET("api/billetera/saldo")
    suspend fun obtenerSaldoBilletera(
        @Header("X-Expected-User") owner: String
    ): Response<BilleteraSaldoResponse>

    @GET("api/billetera/movimientos")
    suspend fun listarMovimientosBilletera(
        @Header("X-Expected-User") owner: String,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<BilleteraMovimientosResponse>

    @POST("api/pagos/qr")
    suspend fun generarQrPago(
        @Body body: GenerarQrRequest,
        @Header("X-Expected-User") owner: String
    ): Response<GenerarQrResponse>

    @GET("api/pagos/pedido/{pedidoId}")
    suspend fun obtenerPagoDelPedido(
        @Path("pedidoId") pedidoId: String,
        @Header("X-Expected-User") owner: String
    ): Response<PagoResponse>

    @GET("api/pagos/{id}")
    suspend fun obtenerPago(
        @Path("id") id: String,
        @Header("X-Expected-User") owner: String
    ): Response<PagoResponse>

    @POST("api/pagos/{id}/confirmar")
    suspend fun confirmarPago(
        @Path("id") id: String,
        @Body body: ConfirmarPagoRequest,
        @Header("X-Expected-User") owner: String
    ): Response<ConfirmarPagoResponse>

    @POST("api/pagos/{id}/autorizar-billetera")
    suspend fun autorizarBilleteraPago(
        @Path("id") id: String,
        @Body body: ConfirmarPagoRequest,
        @Header("X-Expected-User") owner: String
    ): Response<AutorizarBilleteraResponse>

    @POST("api/pagos/ajustes/{id}/confirmar")
    suspend fun confirmarAjustePago(
        @Path("id") id: String,
        @Body body: ConfirmarPagoRequest,
        @Header("X-Expected-User") owner: String
    ): Response<ConfirmarAjusteResponse>
}
