# Keep app entry
-keep class com.megamarket.cliente.MainActivity { *; }
-keep class com.megamarket.cliente.MegaMarketClienteApplication { *; }
-keep class com.megamarket.cliente.BuildConfig { *; }

# Kotlin / Compose
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn androidx.compose.**
-keep class androidx.navigation.compose.** { *; }

# Retrofit / Gson (API REST futura; no conecta a Neon)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.megamarket.cliente.data.remote.** { *; }
-keep class com.megamarket.cliente.worker.StockSyncWorker { *; }
