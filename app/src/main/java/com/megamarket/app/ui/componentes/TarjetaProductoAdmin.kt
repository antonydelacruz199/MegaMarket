package com.megamarket.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.megamarket.app.data.local.AlmacenImagenes
import com.megamarket.app.ui.tema.MegaAdvertencia
import com.megamarket.app.ui.tema.MegaContenedorAdvertencia
import com.megamarket.app.ui.tema.MegaContenedorError
import com.megamarket.app.ui.tema.MegaContenedorNeutro
import com.megamarket.app.ui.tema.MegaContenedorPrimario
import com.megamarket.app.ui.tema.MegaContenedorSecundario
import com.megamarket.app.ui.tema.MegaError
import com.megamarket.app.ui.tema.MegaNeutro
import com.megamarket.app.ui.tema.MegaPrimario
import com.megamarket.app.ui.tema.MegaSecundario
import com.megamarket.modelo.Producto
import com.megamarket.modelo.formatearSoles

@Composable
fun TarjetaProductoAdmin(
    producto: Producto,
    alEditar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val tono = tonoProducto(producto)
    Card(
        onClick = alEditar,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(tono)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ImagenProducto(
                        identificador = producto.imagenKey,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(84.dp),
                        descripcion = producto.nombre,
                        cargar = { AlmacenImagenes.bitmap(contexto, producto.imagenKey, 240) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = producto.nombre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = textoMarca(producto),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (producto.descripcion.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = producto.descripcion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = producto.precioVigenteCentimos.formatearSoles(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (producto.ofertaValida) MegaSecundario else MegaPrimario
                            )
                            if (producto.ofertaValida) {
                                Text(
                                    text = producto.precioCentimos.formatearSoles(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                        }
                    }
                    IconButton(onClick = alEditar) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar producto",
                            tint = MegaPrimario
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EtiquetaProducto(
                        texto = if (producto.activo) "Activo" else "Inactivo",
                        fondo = if (producto.activo) MegaContenedorPrimario else MegaContenedorNeutro,
                        tinta = if (producto.activo) MegaPrimario else MegaNeutro
                    )
                    EtiquetaProducto(
                        texto = textoStock(producto),
                        fondo = fondoStock(producto),
                        tinta = tintaStock(producto)
                    )
                    if (producto.esOferta) {
                        EtiquetaProducto(
                            texto = "Oferta",
                            fondo = MegaContenedorSecundario,
                            tinta = MegaSecundario
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EtiquetaProducto(
    texto: String,
    fondo: Color,
    tinta: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = fondo
    ) {
        Text(
            text = texto,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tinta
        )
    }
}

private fun textoMarca(producto: Producto): String {
    return if (producto.categoriaId > 0L) {
        "${producto.marca} · Categoría ${producto.categoriaId}"
    } else {
        producto.marca
    }
}

private fun textoStock(producto: Producto): String {
    return when {
        producto.agotado -> "Sin stock"
        producto.stock == 1 -> "1 unidad"
        else -> "${producto.stock} unidades"
    }
}

private fun fondoStock(producto: Producto): Color {
    return when {
        producto.agotado -> MegaContenedorError
        producto.stock in 1..5 -> MegaContenedorAdvertencia
        else -> MegaContenedorPrimario
    }
}

private fun tintaStock(producto: Producto): Color {
    return when {
        producto.agotado -> MegaError
        producto.stock in 1..5 -> MegaAdvertencia
        else -> MegaPrimario
    }
}

private fun tonoProducto(producto: Producto): Color {
    return when {
        !producto.activo -> MegaNeutro
        producto.agotado -> MegaError
        producto.stock in 1..5 -> MegaAdvertencia
        producto.esOferta -> MegaSecundario
        else -> MegaPrimario
    }
}
