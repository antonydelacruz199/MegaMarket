package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.ActualizarStockRequest
import com.megamarket.cliente.data.remote.dto.ActualizarStockResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * LEGACY — no usar en flujo activo.
 * El inventario se sincroniza con [InventarioApi] (movimientos / delta).
 */
@Deprecated("Usar InventarioApi.crearMovimiento")
interface StockApi {
    @PATCH("api/productos/{uuid}/stock")
    suspend fun actualizarStock(
        @Path("uuid") uuidProducto: String,
        @Body body: ActualizarStockRequest
    ): Response<ActualizarStockResponse>
}
