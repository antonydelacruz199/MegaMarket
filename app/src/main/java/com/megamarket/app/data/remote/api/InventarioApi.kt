package com.megamarket.app.data.remote.api

import com.megamarket.app.data.remote.dto.MovimientoInventarioRequest
import com.megamarket.app.data.remote.dto.MovimientoInventarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface InventarioApi {
    @POST("api/inventario/movimientos")
    suspend fun crearMovimiento(
        @Body body: MovimientoInventarioRequest
    ): Response<MovimientoInventarioResponse>
}
