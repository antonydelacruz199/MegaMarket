package com.megamarket.cliente.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megamarket.cliente.ui.theme.Espaciado
import com.megamarket.cliente.ui.theme.FeriaCoral
import com.megamarket.cliente.ui.theme.FeriaPetalo
import com.megamarket.cliente.ui.theme.MegaSobrePrimario
import com.megamarket.modelo.Producto
import com.megamarket.modelo.SolicitudImagenProducto

@Composable
fun TarjetaProducto(
    producto: Producto,
    alPulsar: () -> Unit,
    solicitudImagen: SolicitudImagenProducto?,
    modifier: Modifier = Modifier,
    esFavorito: Boolean = false,
    alAlternarFavorito: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (producto.ofertaValida) {
                FeriaPetalo
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (producto.ofertaValida) {
            BorderStroke(1.5.dp, FeriaCoral.copy(alpha = 0.7f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(Espaciado.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImagenProducto(
                solicitud = solicitudImagen,
                modifier = Modifier
                    .padding(end = Espaciado.md)
                    .size(76.dp),
                descripcion = producto.nombre
            )
            Column(modifier = Modifier.weight(1f)) {
                if (producto.ofertaValida) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = FeriaCoral
                    ) {
                        Text(
                            text = "Yapa de feria",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MegaSobrePrimario
                        )
                    }
                    Spacer(modifier = Modifier.height(Espaciado.sm))
                }
                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = producto.marca,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Espaciado.xs))
                FilaPrecio(producto)
            }
            if (alAlternarFavorito != null) {
                IconButton(onClick = alAlternarFavorito) {
                    Icon(
                        imageVector = if (esFavorito) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (esFavorito) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (esFavorito) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
