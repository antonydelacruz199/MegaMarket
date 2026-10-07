package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.CrearPedidoRequest
import com.megamarket.cliente.data.remote.dto.CrearPedidoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PedidoApi {
    @POST("api/pedidos")
    suspend fun crearPedido(@Body body: CrearPedidoRequest): Response<CrearPedidoResponse>
}
