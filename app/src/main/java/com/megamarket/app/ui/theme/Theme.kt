package com.megamarket.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MegaPrimary,
    onPrimary = MegaOnPrimary,
    primaryContainer = MegaPrimaryContainer,
    onPrimaryContainer = MegaPrimaryDark,
    secondary = MegaSecondary,
    onSecondary = MegaOnPrimary,
    secondaryContainer = MegaSecondaryContainer,
    onSecondaryContainer = MegaOnSurface,
    tertiary = MegaOffer,
    onTertiary = MegaOnPrimary,
    background = MegaBackground,
    onBackground = MegaOnSurface,
    surface = MegaSurface,
    onSurface = MegaOnSurface,
    onSurfaceVariant = MegaOnSurfaceVariant,
    error = MegaError,
    onError = MegaOnPrimary,
    outline = MegaOnSurfaceVariant.copy(alpha = 0.4f)
)

@Composable
fun MegaMarketTheme(
    // Reserved for a future dark ColorScheme; light brand palette only in this stage.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
