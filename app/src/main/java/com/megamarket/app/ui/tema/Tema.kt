package com.megamarket.app.ui.tema

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
    onSurfaceVariant = MegaSobreSuperficieVariante,
    error = MegaError,
    onError = MegaSobrePrimario,
    outline = MegaSobreSuperficieVariante.copy(alpha = 0.4f)
)

@Composable
fun TemaMegaMarket(
    // Reservado para un esquema oscuro futuro; en esta etapa solo la paleta clara.
    @Suppress("UNUSED_PARAMETER") temaOscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit
) {
    val esquemaColor = EsquemaColorClaro
    val vista = LocalView.current

    if (!vista.isInEditMode) {
        SideEffect {
            val ventana = (vista.context as Activity).window
            WindowCompat.getInsetsController(ventana, vista).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = esquemaColor,
        typography = Tipografia,
        content = contenido
    )
}
