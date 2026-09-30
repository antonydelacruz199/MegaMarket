package com.megamarket.cliente.ui.pantallas.producto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.modelo.nombreCategoria
import com.megamarket.cliente.ui.componentes.BarraSuperior
import com.megamarket.cliente.ui.componentes.CintaFeria
import com.megamarket.cliente.ui.componentes.ImagenProducto
import com.megamarket.cliente.ui.componentes.EstadoVacio
import com.megamarket.cliente.ui.componentes.TextoPrecio
import com.megamarket.cliente.viewmodel.ViewModelDetalle
import com.megamarket.modelo.Producto

@Composable
fun PantallaDetalleProducto(
    viewModel: ViewModelDetalle,
    alVolver: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val producto = estado.producto

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = "Producto",
                alPulsarNavegacion = alVolver,
                acciones = {
                    if (producto != null) {
                        IconButton(onClick = viewModel::alternarFavorito) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = if (estado.esFavorito) {
                                    "Quitar de favoritos"
                                } else {
                                    "Agregar a favoritos"
                                },
                                tint = if (estado.esFavorito) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { relleno ->
        when {
            estado.cargando -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            producto == null -> EstadoVacio(
                icono = Icons.Default.Add,
                titulo = "Producto no disponible",
                descripcion = estado.error ?: "No se encontró el producto",
                modifier = Modifier.padding(relleno)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                ImagenProducto(
                    productoId = producto.id,
                    imagenKey = producto.imagenKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    lado = 1080,
                    descripcion = producto.nombre
                )
                if (producto.ofertaValida) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CintaFeria(descuento = porcentajeYapa(producto))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(producto.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(producto.marca, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = nombreCategoria(producto.categoriaId),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextoPrecio(producto)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (producto.agotado) "Agotado" else "Stock: ${producto.stock}",
                    color = if (producto.agotado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(producto.descripcion, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))
                if (producto.agotado) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Agotado")
                    }
                } else if (estado.cantidadEnCarrito == 0) {
                    Button(
                        onClick = viewModel::agregar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Agregar al carrito") }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("En el carrito", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.cambiarCantidad(estado.cantidadEnCarrito - 1) },
                                enabled = estado.cantidadEnCarrito > 1
                            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            Text("${estado.cantidadEnCarrito}", style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                onClick = { viewModel.cambiarCantidad(estado.cantidadEnCarrito + 1) },
                                enabled = estado.cantidadEnCarrito < producto.stock
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
            }
        }
    }
}

private fun porcentajeYapa(producto: Producto): Int? {
    val oferta = producto.precioOfertaCentimos
    if (!producto.ofertaValida || oferta == null || producto.precioCentimos <= 0L) return null
    return ((producto.precioCentimos - oferta) * 100 / producto.precioCentimos).toInt()
}
