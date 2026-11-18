package com.megamarket.picker.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val ownerProvider: () -> String? = { null },
    private val tokenForOwner: (String) -> String? = { null }
) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { cadena ->
            val original = cadena.request()
            val builder = original.newBuilder()
            val ruta = original.url.encodedPath
            val esImagen = ruta.contains("/api/medios/") &&
                original.url.queryParameter("download") == "1"
            builder.header(
                "Accept",
                if (esImagen) "image/jpeg, image/png, image/webp, */*" else "application/json"
            )
            val esLogin = ruta.endsWith("/api/auth/login") || ruta.endsWith("api/auth/login")
            if (!esLogin) {
                val expected = original.header("X-Expected-User")
                if (expected != null && expected != ownerProvider()) {
                    throw IOException(MENSAJE_CUENTA_CAMBIADA)
                }
                builder.removeHeader("X-Expected-User")
                val token = (if (expected != null) tokenForOwner(expected) else tokenProvider())
                    ?.trim()
                    .orEmpty()
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            cadena.proceed(builder.build())
        }
        .build()

    fun crear(): ApiService {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    companion object {
        const val MENSAJE_CUENTA_CAMBIADA = "La cuenta activa cambió"
    }
}
