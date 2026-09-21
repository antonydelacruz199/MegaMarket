# Keep app entry
-keep class com.megamarket.app.MainActivity { *; }

# Kotlin / Compose
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn androidx.compose.**
-keep class androidx.navigation.compose.** { *; }
