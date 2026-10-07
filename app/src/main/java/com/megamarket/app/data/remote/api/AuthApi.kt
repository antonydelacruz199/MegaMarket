package com.megamarket.app.data.remote.api

import com.megamarket.app.data.remote.dto.LoginRequest
import com.megamarket.app.data.remote.dto.LoginResponse
import com.megamarket.app.data.remote.dto.UsuarioRemotoDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>
}

data class MeResponse(val usuario: UsuarioRemotoDto)
