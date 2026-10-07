package com.megamarket.app.data.remote.api

import com.megamarket.app.data.remote.dto.LoginRequest
import com.megamarket.app.data.remote.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>
}
