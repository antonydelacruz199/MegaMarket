package com.megamarket.cliente.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megamarket.cliente.ui.theme.FeriaCoral
import com.megamarket.cliente.ui.theme.FeriaPetalo
import com.megamarket.cliente.ui.theme.FeriaRosa
import com.megamarket.cliente.ui.theme.FeriaSol
import com.megamarket.cliente.ui.theme.MegaPrimario
import com.megamarket.cliente.ui.theme.MegaSobrePrimario

@Composable
fun BannerFeriaPrimavera(
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    etiquetaAccion: String? = null,
    alPulsar: (() -> Unit)? = null
) {
    val forma = RoundedCornerShape(if (compacto) 16.dp else 22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(
                Brush.linearGradient(
                    listOf(FeriaCoral, FeriaRosa, MegaPrimario)
                )
            )
            .then(
                if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier
            )
    ) {
        PuntoFeria(
            color = FeriaSol,
            tamano = if (compacto) 42.dp else 72.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-16).dp, y = (-18).dp)
        )
        PuntoFeria(
            color = FeriaPetalo.copy(alpha = 0.85f),
            tamano = if (compacto) 28.dp else 48.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 16.dp)
        )
        Column(modifier = Modifier.padding(if (compacto) 14.dp else 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TEMPORADA EN LIMA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeriaPetalo
                    )
                    Text(
                        text = "Feria de Primavera",
                        style = if (compacto) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.Bold,
                        color = MegaSobrePrimario
                    )
                }
                FlorPrimavera(modifier = Modifier.padding(start = 8.dp))
            }
            if (!compacto) {
                Text(
                    text = "La yapa de tu mercado de barrio: colores de estación y precios que invitan a llevarse algo más.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MegaSobrePrimario,
                    modifier = Modifier.padding(top = 8.dp, end = 24.dp)
                )
            } else {
                Text(
                    text = "Precios de feria, como en el mercado de tu barrio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MegaSobrePrimario,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                )
            }
            if (etiquetaAccion != null) {
                Surface(
                    modifier = Modifier.padding(top = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MegaSobrePrimario
                ) {
                    Text(
                        text = etiquetaAccion,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = FeriaCoral
                    )
                }
            }
        }
    }
}

@Composable
fun CintaFeria(
    descuento: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(listOf(FeriaSol, FeriaCoral, FeriaRosa))
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlorPrimavera()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Feria de Primavera · Lima",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MegaSobrePrimario
                )
                Text(
                    text = "Precio de barrio, como la yapa del mercado de la esquina.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MegaSobrePrimario
                )
            }
        }
        if (descuento != null && descuento > 0) {
            Surface(shape = RoundedCornerShape(8.dp), color = MegaSobrePrimario) {
                Text(
                    text = "−$descuento% de yapa",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeriaCoral
                )
            }
        }
        Text(
            text = "Guárdalo en favoritos o agrégalo al carrito antes de que se acabe.",
            style = MaterialTheme.typography.bodySmall,
            color = MegaSobrePrimario
        )
    }
}

@Composable
private fun FlorPrimavera(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(54.dp), contentAlignment = Alignment.Center) {
        listOf(
            Alignment.TopCenter,
            Alignment.CenterStart,
            Alignment.CenterEnd,
            Alignment.BottomCenter
        ).forEach { alineacion ->
            Box(
                modifier = Modifier
                    .align(alineacion)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(FeriaSol)
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(FeriaRosa)
        )
    }
}

@Composable
private fun PuntoFeria(
    color: Color,
    tamano: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.55f))
    )
}
