package com.megamarket.cliente.ui.screens.pagos

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.data.repository.MovimientoBilleteraCliente
import com.megamarket.cliente.data.repository.textoFechaHora
import com.megamarket.cliente.viewmodel.BilleteraViewModel
import com.megamarket.modelo.formatearSoles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBilletera(
    viewModel: BilleteraViewModel,
    alVolver: () -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi billetera") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            estado.cargando && estado.cuenta == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }

            estado.cuenta == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    estado.error ?: "No se pudo cargar la billetera",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = viewModel::cargar) { Text("Reintentar") }
            }

            else -> {
                val cuenta = checkNotNull(estado.cuenta)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (estado.cargando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Saldo disponible", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        cuenta.saldoCentimos.formatearSoles(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    estado.saldoInicialCentimos?.let {
                                        Text(
                                            "Saldo inicial: ${it.formatearSoles()}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        estado.error?.let { mensaje ->
                            item { Text(mensaje, color = MaterialTheme.colorScheme.error) }
                        }
                        item {
                            Text(
                                "Historial de movimientos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (estado.movimientos.isEmpty()) {
                            item { Text("Aún no tienes movimientos.") }
                        } else {
                            items(estado.movimientos, key = { it.id }) { movimiento ->
                                FilaMovimiento(movimiento)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaMovimiento(movimiento: MovimientoBilleteraCliente) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = textoTipoMovimiento(movimiento.tipo),
                fontWeight = FontWeight.Medium
            )
            movimiento.descripcion?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            movimiento.pedidoId?.let {
                Text("Pedido ${it.take(8)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                textoFechaHora(movimiento.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val monto = movimiento.montoConSigno
            Text(
                text = (if (monto > 0) "+" else "") + monto.formatearSoles(),
                fontWeight = FontWeight.Bold,
                color = if (movimiento.esCredito) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                "Saldo: ${movimiento.saldoPosteriorCentimos.formatearSoles()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal fun textoTipoMovimiento(tipo: String): String = when (tipo.trim().uppercase()) {
    "COBRO_PEDIDO" -> "Pago de pedido"
    "DEVOLUCION_CLIENTE" -> "Devolución"
    "AJUSTE_DESCUENTO_CLIENTE" -> "Ajuste de pedido"
    "DEBITO_COMERCIO_DEVOLUCION" -> "Devolución (comercio)"
    "" -> "Movimiento"
    else -> tipo.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
