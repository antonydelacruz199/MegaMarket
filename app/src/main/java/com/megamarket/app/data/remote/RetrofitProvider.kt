package com.megamarket.app.data.remote

import com.megamarket.app.data.remote.api.AuthApi
import com.megamarket.app.data.remote.api.CategoriaApi
import com.megamarket.app.data.remote.api.InventarioApi
import com.megamarket.app.data.remote.api.ProductoApi
import com.megamarket.app.data.remote.api.StockApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitProvider private constructor(
    val productoApi: ProductoApi,
    val categoriaApi: CategoriaApi,
    val authApi: AuthApi,
    val inventarioApi: InventarioApi,
    @Deprecated("Legacy stock absoluto; usar InventarioApi")
    val stockApi: StockApi
) {
    companion object {
        fun crear(
            baseUrl: String,
            tokenProvider: (() -> String?)? = null
        ): RetrofitProvider? {
            val normalizada = baseUrl.trim()
            if (normalizada.isEmpty()) return null
            val conBarra = if (normalizada.endsWith("/")) normalizada else "$normalizada/"

            val authInterceptor = Interceptor { cadena ->
                val original = cadena.request()
                val esLogin = original.url.encodedPath.endsWith("/api/auth/login")
                val token = if (!esLogin) tokenProvider?.invoke() else null
                val request = if (!token.isNullOrBlank()) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build()
                } else {
                    original.newBuilder()
                        .header("Accept", "application/json")
                        .build()
                }
                cadena.proceed(request)
            }

            val cliente = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(conBarra)
                .client(cliente)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return RetrofitProvider(
                productoApi = retrofit.create(ProductoApi::class.java),
                categoriaApi = retrofit.create(CategoriaApi::class.java),
                authApi = retrofit.create(AuthApi::class.java),
                inventarioApi = retrofit.create(InventarioApi::class.java),
                stockApi = retrofit.create(StockApi::class.java)
            )
        }
    }
}
