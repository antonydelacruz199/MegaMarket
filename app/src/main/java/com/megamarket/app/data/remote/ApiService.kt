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
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/categorias")
    suspend fun listarCategorias(): Response<CategoriasResponse>

    @GET("api/productos")
    suspend fun listarProductos(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("q") q: String? = null
    ): Response<ProductosResponse>

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

    /** Saldo de la cuenta comercio MegaMarket (solo ADMIN). Simulación académica. */
    @GET("api/billetera/comercio")
    suspend fun obtenerBilleteraComercio(): Response<BilleteraSaldoResponse>

    @GET("api/billetera/movimientos")
    suspend fun listarMovimientosBilletera(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<BilleteraMovimientosResponse>

    // ---------- Fase 7: proveedores (solo ADMINISTRADOR) ----------

    @GET("api/proveedores")
    suspend fun listarProveedores(
        @Query("q") q: String? = null,
        @Query("activo") activo: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<ProveedoresResponse>

    @GET("api/proveedores/{id}")
    suspend fun obtenerProveedor(@Path("id") id: String): Response<ProveedorResponse>

    @POST("api/proveedores")
    suspend fun crearProveedor(@Body body: CrearProveedorRequest): Response<ProveedorResponse>

    @PATCH("api/proveedores/{id}")
    suspend fun actualizarProveedor(
        @Path("id") id: String,
        @Body body: ActualizarProveedorRequest
    ): Response<ProveedorResponse>

    @GET("api/proveedores/{id}/ordenes")
    suspend fun listarOrdenesDeProveedor(
        @Path("id") id: String,
        @Query("estado") estado: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<OrdenesCompraResponse>

    // ---------- Fase 7: órdenes de compra (solo ADMINISTRADOR) ----------

    @GET("api/compras/ordenes")
    suspend fun listarOrdenesCompra(
        @Query("estado") estado: String? = null,
        @Query("proveedorId") proveedorId: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<OrdenesCompraResponse>

    @GET("api/compras/ordenes/{id}")
    suspend fun obtenerOrdenCompra(@Path("id") id: String): Response<OrdenCompraResponse>

    @POST("api/compras/ordenes")
    suspend fun crearOrdenCompra(@Body body: CrearOrdenCompraRequest): Response<OrdenCompraResponse>

    @POST("api/compras/ordenes/{id}/aprobar")
    suspend fun aprobarOrdenCompra(
        @Path("id") id: String,
        @Body body: AprobarOrdenRequest
    ): Response<OrdenCompraResponse>

    @POST("api/compras/ordenes/{id}/cancelar")
    suspend fun cancelarOrdenCompra(@Path("id") id: String): Response<OrdenCompraResponse>

    /** Pago por simulador académico "Banco Andino". Un 409 puede traer la transferencia en el cuerpo. */
    @POST("api/compras/ordenes/{id}/pago")
    suspend fun pagarOrdenCompra(
        @Path("id") id: String,
        @Body body: PagarOrdenRequest
    ): Response<OrdenCompraResponse>

    @POST("api/compras/ordenes/{id}/recepciones")
    suspend fun registrarRecepcionOrden(
        @Path("id") id: String,
        @Body body: RecepcionOrdenRequest
    ): Response<OrdenCompraResponse>

    @GET("api/compras/ordenes/{id}/recepciones")
    suspend fun listarRecepcionesOrden(@Path("id") id: String): Response<RecepcionesResponse>

    @GET("api/compras/transferencias/{uuidOperacion}")
    suspend fun consultarTransferenciaProveedor(
        @Path("uuidOperacion") uuidOperacion: String
    ): Response<TransferenciaResponse>

    @Multipart
    @POST("api/medios/productos/{productoId}")
    suspend fun subirImagenProducto(
        @Path("productoId") productoId: String,
        @Part archivo: MultipartBody.Part
    ): Response<SubidaImagenProductoResponse>
}
