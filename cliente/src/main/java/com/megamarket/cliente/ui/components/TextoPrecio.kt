package com.megamarket.cliente.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.megamarket.modelo.Producto
import com.megamarket.modelo.formatearSoles

@Composable
fun TextoPrecio(producto: Producto) {
    if (producto.ofertaValida) {
        Column {
            Text(
                text = producto.precioVigenteCentimos.formatearSoles(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = producto.precioCentimos.formatearSoles(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough
            )
        }
    } else {
        Text(
            text = producto.precioCentimos.formatearSoles(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FilaPrecio(producto: Producto) {
    Row {
        TextoPrecio(producto)
        if (producto.agotado) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Agotado",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
