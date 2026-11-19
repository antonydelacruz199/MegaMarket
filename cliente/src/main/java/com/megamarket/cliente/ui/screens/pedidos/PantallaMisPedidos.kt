package com.megamarket.cliente.ui.screens.pedidos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.data.repository.FiltroMisPedidos
import com.megamarket.cliente.data.repository.PedidoResumenCliente
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.viewmodel.MisPedidosViewModel
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaMisPedidos(
    viewModel: MisPedidosViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit,
    alAbrirDetalle: (String) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    EstructuraCliente(
        rutaActual = Ruta.MisPedidos.ruta,
        titulo = "Mis Pedidos",
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FiltroMisPedidos.entries.toList()) { filtro ->
                    FilterChip(
                        selected = estado.filtro == filtro,
                        onClick = { viewModel.seleccionarFiltro(filtro) },
                        label = { Text(filtro.etiqueta) }
                    )
                }
            }
            when {
                estado.cargando -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                estado.error != null && estado.pedidos.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = viewModel::cargar) { Text("Reintentar") }
                    }
                }
                else -> {
                    if (estado.error != null) {
                        Text(
                            text = estado.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (estado.pedidos.isEmpty()) {
                        Text(
                            text = "No hay pedidos en este filtro.",
                            modifier = Modifier.padding(24.dp)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(estado.pedidos, key = { it.id }) { pedido ->
                                TarjetaPedido(pedido) { alAbrirDetalle(pedido.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPedido(pedido: PedidoResumenCliente, alAbrir: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alAbrir)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Pedido ${pedido.id.take(8)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(pedido.fecha ?: "Sin fecha")
        Text(pedido.totalCentimos.formatearSoles(), fontWeight = FontWeight.Medium)
        Text("Estado: ${pedido.estado}")
        if (!pedido.franjaEtiqueta.isNullOrBlank()) {
            Text("Entrega: ${pedido.franjaEtiqueta}")
        } else if (!pedido.franjaEntregaId.isNullOrBlank()) {
            Text("Entrega: horario por confirmar")
        }
        if (!pedido.modalidadEtiqueta.isNullOrBlank() || !pedido.modalidadPago.isNullOrBlank()) {
            Text("Pago: ${pedido.modalidadVisible}")
        }
        if (pedido.tienePropuestaPendiente) {
            Text(
                text = "Propuesta de sustitución pendiente",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(onClick = alAbrir) { Text("Ver detalle") }
    }
}
