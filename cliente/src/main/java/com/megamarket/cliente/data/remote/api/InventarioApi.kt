package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.MovimientoInventarioRequest
import com.megamarket.cliente.data.remote.dto.MovimientoInventarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Único responsable futuro de modificar stock remoto.
 * POST /api/pedidos NO descuenta inventario.
 */
interface InventarioApi {
    @POST("api/inventario/movimientos")
    suspend fun crearMovimiento(
        @Body body: MovimientoInventarioRequest
    ): Response<MovimientoInventarioResponse>
}
