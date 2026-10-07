package com.megamarket.cliente.data.remote.api

import com.megamarket.cliente.data.remote.dto.LoginRequest
import com.megamarket.cliente.data.remote.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Preparado. El flujo de sesión actual permanece local. */
interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>
}
