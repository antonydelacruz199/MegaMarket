package com.megamarket.picker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.picker.ui.theme.MegaContenedorAdvertencia
import com.megamarket.picker.ui.theme.MegaContenedorExito
import com.megamarket.picker.ui.theme.MegaContenedorPrimario
import com.megamarket.picker.ui.theme.MegaContenedorSecundario
import com.megamarket.picker.ui.theme.MegaAdvertencia
import com.megamarket.picker.ui.theme.MegaExito
import com.megamarket.picker.ui.theme.MegaPrimario
import com.megamarket.picker.ui.theme.MegaSecundario

enum class TonoChip {
    Primario,
    Secundario,
    Exito,
    Advertencia,
    Neutro
}

@Composable
fun ChipEstado(
    texto: String,
    tono: TonoChip,
    modifier: Modifier = Modifier
) {
    val (fondo, contenido) = when (tono) {
        TonoChip.Primario -> MegaContenedorPrimario to MegaPrimario
        TonoChip.Secundario -> MegaContenedorSecundario to MegaSecundario
        TonoChip.Exito -> MegaContenedorExito to MegaExito
        TonoChip.Advertencia -> MegaContenedorAdvertencia to MegaAdvertencia
        TonoChip.Neutro -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = fondo
    ) {
        Text(
            text = texto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contenido
        )
    }
}
