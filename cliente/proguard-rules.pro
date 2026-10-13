# Keep app entry
-keep class com.megamarket.cliente.MainActivity { *; }
-keep class com.megamarket.cliente.MegaMarketClienteApplication { *; }

# Kotlin / Compose
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn androidx.compose.**
-keep class androidx.navigation.compose.** { *; }
