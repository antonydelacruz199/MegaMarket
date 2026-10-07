package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.ActualizarStockRequest
import com.megamarket.cliente.data.remote.dto.ActualizarStockResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * Contrato REST previsto:
 * PATCH /api/productos/{uuid}/stock
 *
 * Falta: API real + mapeo Long local → UUID de Neon.
 * Mientras no exista, SyncRepository no marca éxito y WorkManager reintenta.
 */
interface StockApi {
    @PATCH("api/productos/{uuid}/stock")
    suspend fun actualizarStock(
        @Path("uuid") uuidProducto: String,
        @Body body: ActualizarStockRequest
    ): Response<ActualizarStockResponse>
}
