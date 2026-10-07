package com.megamarket.cliente.data.remote

import com.megamarket.cliente.data.remote.api.AuthApi
import com.megamarket.cliente.data.remote.api.CategoriaApi
import com.megamarket.cliente.data.remote.api.InventarioApi
import com.megamarket.cliente.data.remote.api.PedidoApi
import com.megamarket.cliente.data.remote.api.ProductoApi
import com.megamarket.cliente.data.remote.api.StockApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Una sola instancia Retrofit cuando hay BASE_URL.
 * Sin URL: null — no se finge conexión a Neon.
 */
class RetrofitProvider private constructor(
    val productoApi: ProductoApi,
    val categoriaApi: CategoriaApi,
    val authApi: AuthApi,
    val pedidoApi: PedidoApi,
    val inventarioApi: InventarioApi,
    @Deprecated("Legacy stock absoluto; usar InventarioApi")
    val stockApi: StockApi
) {
    companion object {
        fun crear(baseUrl: String): RetrofitProvider? {
            val normalizada = baseUrl.trim()
            if (normalizada.isEmpty()) return null
            val conBarra = if (normalizada.endsWith("/")) normalizada else "$normalizada/"
            val cliente = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
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
                pedidoApi = retrofit.create(PedidoApi::class.java),
                inventarioApi = retrofit.create(InventarioApi::class.java),
                stockApi = retrofit.create(StockApi::class.java)
            )
        }
    }
}
