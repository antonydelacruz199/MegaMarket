package com.megamarket.cliente.ui.screens.pedidos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.data.repository.FiltroMisPedidos
import com.megamarket.cliente.data.repository.PedidoResumenCliente
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.ui.components.EstructuraCliente
import com.megamarket.cliente.ui.navigation.Ruta
import com.megamarket.cliente.viewmodel.MisPedidosViewModel
import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles
import kotlinx.coroutines.flow.distinctUntilChanged

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
            OutlinedTextField(
                value = estado.consulta,
                onValueChange = viewModel::actualizarConsulta,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Buscar por pedido o producto") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                shape = RoundedCornerShape(12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FiltroMisPedidos.entries.toList()) { filtro ->
                    FilterChip(
                        selected = estado.filtro == filtro,
                        onClick = { viewModel.seleccionarFiltro(filtro) },
                        label = { Text(filtro.etiqueta) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            when {
                estado.cargando && estado.pedidos.isEmpty() -> {
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
                        Button(onClick = { viewModel.cargar(true) }) { Text("Reintentar") }
                    }
                }
                estado.pedidos.isEmpty() -> {
                    EstadoVacio(
                        icono = Icons.Default.ReceiptLong,
                        titulo = "Sin pedidos",
                        descripcion = if (estado.consulta.isNotBlank()) {
                            "No hay resultados para “${estado.consulta}”."
                        } else {
                            "No hay pedidos en este filtro."
                        },
                        etiquetaAccion = "Actualizar",
                        alPulsarAccion = { viewModel.cargar(true) }
                    )
                }
                else -> {
                    if (estado.error != null) {
                        Text(
                            text = estado.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    val lista = rememberLazyListState()
                    LaunchedEffect(estado.filtro, estado.consulta) {
                        lista.scrollToItem(0)
                    }
                    LaunchedEffect(lista, estado.hayMas, estado.cargandoMas, estado.filtro) {
                        snapshotFlow {
                            val info = lista.layoutInfo
                            val ultimo = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            ultimo >= info.totalItemsCount - 2
                        }
                            .distinctUntilChanged()
                            .collect { cerca ->
                                if (cerca && estado.hayMas && !estado.cargandoMas && !estado.cargando) {
                                    viewModel.cargarMas()
                                }
                            }
                    }
                    LazyColumn(
                        state = lista,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(estado.pedidos, key = { it.id }) { pedido ->
                            TarjetaPedido(pedido) { alAbrirDetalle(pedido.id) }
                        }
                        if (estado.hayMas) {
                            item(key = "cargar_mas_${estado.filtro}_${estado.pagina}") {
                                if (estado.cargandoMas) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::cargarMas,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Cargar más") }
                                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = alAbrir),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pedido ${pedido.id.take(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pedido.fecha?.let { formatearFechaHoraPresentacion(it) }
                            ?.takeIf { it != "—" }
                            ?: "Sin fecha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChipEstadoPedido(pedido.estado)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                pedido.totalCentimos.formatearSoles(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (!pedido.franjaEtiqueta.isNullOrBlank()) {
                Text(
                    "Entrega: ${pedido.franjaEtiqueta}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!pedido.franjaEntregaId.isNullOrBlank()) {
                Text(
                    "Entrega: horario por confirmar",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!pedido.modalidadEtiqueta.isNullOrBlank() || !pedido.modalidadPago.isNullOrBlank()) {
                Text(
                    "Pago: ${pedido.modalidadVisible}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pedido.tienePropuestaPendiente) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Hay novedades de sustitución o sin stock",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ver detalle",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ChipEstadoPedido(estado: String) {
    val e = estado.trim().uppercase()
    val (fondo, texto) = when (e) {
        "ENTREGADO" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "CANCELADO" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "EN_CAMINO", "LISTO" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = RoundedCornerShape(20.dp), color = fondo) {
        Text(
            text = estado.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = texto
        )
    }
}
