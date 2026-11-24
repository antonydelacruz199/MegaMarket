package com.megamarket.app.ui.screens.presentacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.LogoMegaMarket
import com.megamarket.app.ui.theme.Espaciado
import com.megamarket.app.ui.theme.MegaPrimario
import com.megamarket.app.ui.theme.MegaPrimarioOscuro
import com.megamarket.app.ui.theme.MegaSecundario
import kotlinx.coroutines.delay

@Composable
fun PantallaPresentacion(
    alIrASesion: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1500)
        alIrASesion()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MegaPrimario, MegaPrimarioOscuro)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(MegaSecundario.copy(alpha = 0.22f))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(Espaciado.xl)
        ) {
            LogoMegaMarket(tamano = 104.dp)
            Spacer(modifier = Modifier.height(Espaciado.xl))
            Text(
                text = "MegaMarket",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Administración",
                style = MaterialTheme.typography.headlineSmall,
                color = MegaSecundario,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Espaciado.md))
            Text(
                text = "Gestiona catálogo, stock y cobros",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Espaciado.xl))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "Huancayo",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
