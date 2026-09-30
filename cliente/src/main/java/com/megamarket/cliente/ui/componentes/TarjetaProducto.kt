package com.megamarket.cliente.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import com.megamarket.cliente.ui.tema.FeriaCoral
import com.megamarket.cliente.ui.tema.FeriaPetalo
import com.megamarket.cliente.ui.tema.MegaSobrePrimario
import com.megamarket.modelo.Producto

@Composable
fun TarjetaProducto(
    producto: Producto,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    esFavorito: Boolean = false,
    alAlternarFavorito: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (producto.ofertaValida) {
                FeriaPetalo
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (producto.ofertaValida) BorderStroke(2.dp, FeriaCoral) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (producto.ofertaValida) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImagenProducto(
                productoId = producto.id,
                imagenKey = producto.imagenKey,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(72.dp),
                lado = 200,
                descripcion = producto.nombre
            )
            Column(modifier = Modifier.weight(1f)) {
                if (producto.ofertaValida) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
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
                    Spacer(modifier = Modifier.height(6.dp))
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
                FilaPrecio(producto)
            }
            if (alAlternarFavorito != null) {
                IconButton(onClick = alAlternarFavorito) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = if (esFavorito) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (esFavorito) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        }
    }
}
