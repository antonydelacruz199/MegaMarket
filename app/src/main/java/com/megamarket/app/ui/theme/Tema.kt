package com.megamarket.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EsquemaColorClaro = lightColorScheme(
    primary = MegaPrimario,
    onPrimary = MegaSobrePrimario,
    primaryContainer = MegaContenedorPrimario,
    onPrimaryContainer = MegaPrimarioOscuro,
    secondary = MegaSecundario,
    onSecondary = MegaSobrePrimario,
    secondaryContainer = MegaContenedorSecundario,
    onSecondaryContainer = MegaSobreSuperficie,
    tertiary = MegaOferta,
    onTertiary = MegaSobrePrimario,
    background = MegaFondo,
    onBackground = MegaSobreSuperficie,
    surface = MegaSuperficie,
    onSurface = MegaSobreSuperficie,
    surfaceVariant = MegaSuperficieVariante,
    onSurfaceVariant = MegaSobreSuperficieVariante,
    error = MegaError,
    onError = MegaSobrePrimario,
    errorContainer = MegaContenedorError,
    onErrorContainer = MegaError,
    outline = MegaOutline,
    outlineVariant = MegaOutline.copy(alpha = 0.55f)
)

@Composable
fun TemaMegaMarket(
    @Suppress("UNUSED_PARAMETER") temaOscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit
) {
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val ventana = (vista.context as Activity).window
            WindowCompat.getInsetsController(ventana, vista).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = EsquemaColorClaro,
        typography = Tipografia,
        shapes = FormasMega,
        content = contenido
    )
}
