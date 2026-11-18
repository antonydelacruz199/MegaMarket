package com.megamarket.picker.ui.screens.picker

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.megamarket.picker.R
import com.megamarket.picker.data.repository.PedidoAsignado
import com.megamarket.picker.data.repository.PedidoDisponible
import com.megamarket.picker.state.ListaPickerUiState
import com.megamarket.picker.ui.formatearSoles
import com.megamarket.picker.ui.textoFecha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicioPicker(
    nombre: String,
    estado: ListaPickerUiState,
    alCargar: () -> Unit,
    alTomar: (String) -> Unit,
    alAbrir: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    LifecycleResumeEffect(Unit) {
        alCargar()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preparacion)) },
                actions = {
                    IconButton(onClick = alCargar) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reintentar))
                    }
                    TextButton(onClick = alCerrarSesion) {
                        Text(stringResource(R.string.cerrar_sesion))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.saludo, nombre.ifBlank { stringResource(R.string.preparacion) }),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )
            if (estado.refrescando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                estado.cargando && estado.disponibles.isEmpty() && estado.asignados.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.cargando_pedidos))
                    }
                }
                estado.error != null && estado.disponibles.isEmpty() && estado.asignados.isEmpty() -> {
                    ErrorCarga(mensaje = estado.error, alReintentar = alCargar)
                }
                else -> {
                    if (estado.error != null) {
                        Text(
                            text = estado.error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                    ListaPedidos(estado = estado, alTomar = alTomar, alAbrir = alAbrir)
                }
            }
        }
    }
}

@Composable
private fun ListaPedidos(
    estado: ListaPickerUiState,
    alTomar: (String) -> Unit,
    alAbrir: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Seccion(stringResource(R.string.mis_pedidos)) }
        if (estado.asignados.isEmpty()) {
            item { Vacio(stringResource(R.string.sin_asignados)) }
        }
        items(estado.asignados, key = { it.id }) { pedido ->
            TarjetaAsignado(pedido, alAbrir)
        }
        item { Seccion(stringResource(R.string.pedidos_disponibles)) }
        if (estado.disponibles.isEmpty()) {
            item { Vacio(stringResource(R.string.sin_disponibles)) }
        }
        items(estado.disponibles, key = { it.id }) { pedido ->
            TarjetaDisponible(
                pedido = pedido,
                tomando = estado.tomandoId == pedido.id,
                bloqueado = estado.tomandoId != null,
                alTomar = alTomar
            )
        }
    }
}

@Composable
private fun TarjetaAsignado(pedido: PedidoAsignado, alAbrir: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { alAbrir(pedido.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(textoPedido(pedido.id), fontWeight = FontWeight.SemiBold)
            Text(textoFecha(pedido.fecha), style = MaterialTheme.typography.bodySmall)
            Text(
                text = stringResource(R.string.productos_count, pedido.cantidadLineas),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(pedido.totalCentimos.formatearSoles(), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TarjetaDisponible(
    pedido: PedidoDisponible,
    tomando: Boolean,
    bloqueado: Boolean,
    alTomar: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(textoPedido(pedido.id), fontWeight = FontWeight.SemiBold)
            Text(textoFecha(pedido.fecha), style = MaterialTheme.typography.bodySmall)
            Text(etiquetaDisponible(pedido), style = MaterialTheme.typography.bodyMedium)
            Text(pedido.totalCentimos.formatearSoles(), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { alTomar(pedido.id) },
                enabled = !bloqueado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (tomando) stringResource(R.string.tomando) else stringResource(R.string.tomar_pedido)
                )
            }
        }
    }
}

@Composable
private fun ErrorCarga(mensaje: String, alReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(mensaje, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = alReintentar) {
            Text(stringResource(R.string.reintentar))
        }
    }
}

@Composable
private fun Seccion(titulo: String) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Vacio(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun textoPedido(id: String): String = "Pedido ${id.take(8)}"

private fun etiquetaDisponible(pedido: PedidoDisponible): String = when {
    pedido.estado == "PAGADO" -> "Pagado"
    pedido.modalidad.equals("CONTRAENTREGA", ignoreCase = true) -> "Contraentrega"
    else -> "Listo para preparar"
}
