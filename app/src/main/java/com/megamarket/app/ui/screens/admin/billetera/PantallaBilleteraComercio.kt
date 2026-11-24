package com.megamarket.app.ui.screens.admin.billetera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.app.data.repository.MovimientoComercio
import com.megamarket.app.ui.components.EstructuraAdmin
import com.megamarket.app.ui.navigation.Ruta
import com.megamarket.app.viewmodel.BilleteraComercioViewModel
import com.megamarket.modelo.formatearFechaHoraPresentacion
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaBilleteraComercio(
    viewModel: BilleteraComercioViewModel,
    alNavegar: (String) -> Unit,
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.cargar()
        onPauseOrDispose { }
    }

    EstructuraAdmin(
        rutaActual = Ruta.BilleteraAdmin.ruta,
        alNavegar = alNavegar,
        alCerrarSesion = alCerrarSesion,
        titulo = "Billetera"
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Saldo disponible", style = MaterialTheme.typography.titleSmall)
                    val billetera = estado.billetera
                    if (billetera != null) {
                        Text(
                            text = billetera.saldoCentimos.formatearSoles(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        billetera.saldoInicialCentimos?.let {
                            Text(
                                text = "Saldo inicial: ${it.formatearSoles()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else if (estado.cargando) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("Sin datos", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            estado.error?.let { mensaje ->
                Text(mensaje, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::cargar) { Text("Reintentar") }
            }
            Text(
                text = "Historial de movimientos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val movimientos = estado.movimientos
            if (estado.billetera != null && movimientos.isEmpty()) {
                Text("Todavía no hay movimientos.", style = MaterialTheme.typography.bodyMedium)
            }
            movimientos.forEach { FilaMovimiento(it) }
            if (estado.hayMas) {
                TextButton(
                    onClick = viewModel::cargarMas,
                    enabled = !estado.cargandoMas
                ) {
                    Text(if (estado.cargandoMas) "Cargando…" else "Cargar más")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilaMovimiento(movimiento: MovimientoComercio) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(textoTipo(movimiento.tipo), fontWeight = FontWeight.SemiBold)
                movimiento.descripcion?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                movimiento.pedidoId?.takeIf { it.isNotBlank() }?.let {
                    Text("Pedido ${it.take(8)}", style = MaterialTheme.typography.bodySmall)
                }
                movimiento.createdAt?.let {
                    Text(
                        formatearFechaHoraPresentacion(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val signo = if (movimiento.esCredito) "+" else "-"
            Text(
                text = "$signo${movimiento.montoCentimos.formatearSoles()}",
                fontWeight = FontWeight.Bold,
                color = if (movimiento.esCredito) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

private fun textoTipo(tipo: String): String = when (tipo.uppercase()) {
    "PAGO_PEDIDO", "PAGO" -> "Pago de pedido"
    "AJUSTE", "AJUSTE_PEDIDO" -> "Ajuste de pedido"
    "" -> "Movimiento"
    else -> tipo.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
