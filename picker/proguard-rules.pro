-keep class com.megamarket.picker.MainActivity { *; }

-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn androidx.compose.**
-keep class androidx.navigation.compose.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.megamarket.picker.data.remote.** { *; }
-keep class com.megamarket.picker.data.local.** { *; }
-dontwarn androidx.room.paging.**
-dontwarn androidx.work.**
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.google.gson.**
