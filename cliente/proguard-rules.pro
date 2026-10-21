# Keep app entry
-keep class com.megamarket.cliente.MainActivity { *; }
-keep class com.megamarket.cliente.MegaMarketClienteApplication { *; }

# Kotlin / Compose
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn androidx.compose.**
-keep class androidx.navigation.compose.** { *; }

# Retrofit / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.megamarket.cliente.data.remote.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.google.gson.**
