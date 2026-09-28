package com.megamarket.cliente.ui.pantallas.carrito

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.ui.componentes.EstadoVacio
import com.megamarket.cliente.ui.componentes.EstructuraCliente
import com.megamarket.cliente.ui.componentes.ImagenProducto
import com.megamarket.cliente.ui.componentes.TextoPrecio
import com.megamarket.cliente.ui.navegacion.Ruta
import com.megamarket.cliente.viewmodel.ViewModelCarrito
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaCarrito(
    viewModel: ViewModelCarrito,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alComprar: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    EstructuraCliente(
        rutaActual = Ruta.Carrito.ruta,
        titulo = "Carrito",
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { relleno ->
        when {
            estado.cargando -> Column(
                modifier = Modifier.fillMaxSize().padding(relleno),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            estado.error != null -> EstadoVacio(
                icono = Icons.Default.ShoppingCart,
                titulo = "No se pudo leer el carrito",
                descripcion = estado.error.orEmpty(),
                modifier = Modifier.padding(relleno)
            )

            estado.lineas.isEmpty() -> EstadoVacio(
                icono = Icons.Default.ShoppingCart,
                titulo = "Carrito vacío",
                descripcion = "Tu carrito está vacío",
                modifier = Modifier.padding(relleno)
            )

            else -> LazyColumn(
                modifier = Modifier.padding(relleno),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.lineas, key = { it.producto.id }) { linea ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ImagenProducto(
                                    productoId = linea.producto.id,
                                    imagenKey = linea.producto.imagenKey,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(56.dp),
                                    lado = 160,
                                    descripcion = linea.producto.nombre
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(linea.producto.nombre, fontWeight = FontWeight.SemiBold)
                                    TextoPrecio(linea.producto)
                                }
                                IconButton(onClick = { viewModel.eliminar(linea.producto.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Quitar del carrito")
                                }
                            }
                            if (linea.producto.agotado) {
                                Text("Agotado", color = MaterialTheme.colorScheme.error)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.disminuir(linea.producto.id, linea.cantidad) },
                                        enabled = linea.cantidad > 1
                                    ) { Text("−") }
                                    Text("${linea.cantidad}")
                                    IconButton(
                                        onClick = { viewModel.aumentar(linea.producto.id, linea.cantidad) },
                                        enabled = linea.cantidad < linea.producto.stock
                                    ) { Text("+") }
                                    Text(
                                        text = linea.subtotalCentimos.formatearSoles(),
                                        modifier = Modifier.padding(start = 12.dp),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    FilaTotal("Subtotal", estado.subtotalCentimos)
                    FilaTotal("Total", estado.totalCentimos)
                    Button(
                        onClick = alComprar,
                        enabled = estado.puedeComprar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) { Text("Continuar compra") }
                }
            }
        }
    }
}

@Composable
private fun FilaTotal(etiqueta: String, centimos: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, style = MaterialTheme.typography.titleMedium)
        Text(centimos.formatearSoles(), fontWeight = FontWeight.Bold)
    }
}
