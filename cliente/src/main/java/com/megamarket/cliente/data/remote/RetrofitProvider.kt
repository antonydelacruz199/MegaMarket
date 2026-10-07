package com.megamarket.cliente.data.remote

import com.megamarket.cliente.data.remote.api.StockApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Crea [StockApi] solo si hay una BASE_URL configurada.
 * No usa credenciales de Neon. Si la URL está vacía, devuelve null y el Worker reintenta.
 */
object RetrofitProvider {

    fun crearStockApi(baseUrl: String): StockApi? {
        val normalizada = baseUrl.trim()
        if (normalizada.isEmpty()) return null
        val conBarra = if (normalizada.endsWith("/")) normalizada else "$normalizada/"
        val cliente = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(conBarra)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StockApi::class.java)
    }
}
