package com.megamarket.cliente.ui.screens.pagos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.data.repository.textoFechaHora
import com.megamarket.cliente.state.PagoQrUiState
import com.megamarket.cliente.ui.components.QrBitmap
import com.megamarket.cliente.viewmodel.PagoQrViewModel
import com.megamarket.modelo.formatearSoles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPagoQr(
    viewModel: PagoQrViewModel,
    alVolver: () -> Unit,
    alCerrarSesion: () -> Unit,
    alPagoConfirmado: () -> Unit = alVolver
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    LaunchedEffect(estado.confirmado) {
        if (estado.confirmado) alPagoConfirmado()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pago del pedido") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            estado.cargando && estado.pago == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }

            estado.pago == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    estado.error ?: "No se pudo preparar el pago",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = viewModel::cargar) { Text("Reintentar") }
            }

            else -> ContenidoPago(
                estado = estado,
                alPagar = viewModel::confirmar,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ContenidoPago(
    estado: PagoQrUiState,
    alPagar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pago = checkNotNull(estado.pago)
    val qr = estado.qr
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Text(
                text = pago.montoCentimos.formatearSoles(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pedido: ${pago.pedidoId ?: estado.pedidoId}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Estado del pago: ${pago.estado}", fontWeight = FontWeight.SemiBold)

            estado.saldoDisponibleCentimos?.let { saldo ->
                Text(
                    "Saldo disponible: ${saldo.formatearSoles()}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            estado.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            if (qr != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Código QR", style = MaterialTheme.typography.titleSmall)
                qr.referencia?.let {
                    Text("Referencia: $it", style = MaterialTheme.typography.bodySmall)
                }
                qr.generadoEn?.let { Text("Generado: ${textoFechaHora(it)}") }
                (qr.expiraEn ?: pago.fechaExpiracionQr)?.let {
                    Text("Vence: ${textoFechaHora(it)}")
                }
                ImagenQr(contenido = qr.contenido)
                Text(
                    "Si escaneas y confirmas el pago, esta pantalla se cerrará sola. También puedes usar el botón.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (estado.generandoQr) {
                Text(
                    "Generando código QR…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = alPagar,
            enabled = !estado.confirmando && !estado.cargando && !estado.confirmado,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (estado.confirmando) "Confirmando…" else "Realizar pago")
        }
    }
}

@Composable
private fun ImagenQr(contenido: String) {
    val bitmap = remember(contenido) { QrBitmap.generar(contenido) }
    Box(
        modifier = Modifier
            .size(240.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Código QR de pago",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("No se pudo dibujar el QR", color = Color.Black, textAlign = TextAlign.Center)
        }
    }
}
