package com.megamarket.cliente.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val ownerProvider: () -> String? = { null },
    private val tokenForOwner: (String) -> String? = { null }
) {
    fun crear(): ApiService {
        val authInterceptor = Interceptor { cadena ->
            val original = cadena.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")
            val ruta = original.url.encodedPath
            val esLogin = ruta.endsWith("/api/auth/login") || ruta.endsWith("api/auth/login")
            if (!esLogin) {
                val expected = original.header("X-Expected-User")
                if (expected != null && expected != ownerProvider()) throw java.io.IOException("La cuenta activa cambió")
                builder.removeHeader("X-Expected-User")
                val token = (if(expected!=null) tokenForOwner(expected) else tokenProvider())?.trim().orEmpty()
                if(expected!=null && expected!=ownerProvider()) throw java.io.IOException("La cuenta activa cambió")
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            cadena.proceed(builder.build())
        }

        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
