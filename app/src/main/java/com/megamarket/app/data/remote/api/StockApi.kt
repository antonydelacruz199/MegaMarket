package com.megamarket.app.data.remote.api

import retrofit2.Response
import retrofit2.http.PATCH
import retrofit2.http.Path

@Deprecated("Usar InventarioApi.crearMovimiento")
interface StockApi {
    @PATCH("api/productos/{uuid}/stock")
    suspend fun actualizarStock(@Path("uuid") uuidProducto: String): Response<Unit>
}
