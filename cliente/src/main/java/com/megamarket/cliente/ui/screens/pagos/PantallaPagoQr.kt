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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.megamarket.cliente.data.repository.ETIQUETA_SIMULACION
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
    alCerrarSesion: () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pago con QR ($ETIQUETA_SIMULACION)") },
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
                SelloSimulacion()
                Spacer(modifier = Modifier.height(16.dp))
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
                alConfirmar = viewModel::confirmar,
                alActualizar = viewModel::consultarEstado,
                alRegenerar = viewModel::cargar,
                alVolver = alVolver,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ContenidoPago(
    estado: PagoQrUiState,
    alConfirmar: () -> Unit,
    alActualizar: () -> Unit,
    alRegenerar: () -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pago = checkNotNull(estado.pago)
    val qr = estado.qr
    Column(modifier = modifier.fillMaxSize()) {
        if (estado.cargando || estado.confirmando || estado.consultandoEstado) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SelloSimulacion()

            Text(
                text = (qr?.importeCentimos ?: pago.montoCentimos).formatearSoles(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pedido: ${pago.pedidoId ?: estado.pedidoId}",
                style = MaterialTheme.typography.bodyMedium
            )
            qr?.generadoEn?.let { Text("Generado: ${textoFechaHora(it)}") }
            (qr?.expiraEn ?: pago.fechaExpiracionQr)?.let {
                if (!estado.confirmado) Text("Vence: ${textoFechaHora(it)}")
            }
            qr?.referencia?.let { Text("Referencia: $it", style = MaterialTheme.typography.bodySmall) }
            Text("Estado del pago: ${pago.estado}", fontWeight = FontWeight.SemiBold)

            if (estado.confirmado) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Pago simulado confirmado",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                pago.numeroOperacionBanco?.let { Text("Operación simulada: $it") }
                estado.pedidoEstado?.let { Text("Estado del pedido: $it") }
                estado.saldoClienteCentimos?.let { Text("Saldo simulado: ${it.formatearSoles()}") }
                Text(
                    "No hubo transferencia bancaria real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
            } else {
                if (qr != null) {
                    ImagenQrSimulado(contenido = qr.contenido)
                    Text(
                        text = qr.etiqueta,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    qr.aviso?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text("Aún no hay un QR disponible para este pago.")
                    OutlinedButton(onClick = alRegenerar, enabled = !estado.cargando) {
                        Text("Generar QR")
                    }
                }
                Text(
                    "Mostrar el QR no confirma el pago. Debes confirmar de forma explícita.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                estado.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Button(
                    onClick = alConfirmar,
                    enabled = !estado.confirmando && !estado.cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (estado.confirmando) "Confirmando…" else "Confirmar pago simulado")
                }
                OutlinedButton(
                    onClick = alActualizar,
                    enabled = !estado.consultandoEstado && !estado.confirmando,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Actualizar estado") }
                if (estado.error != null && qr != null) {
                    OutlinedButton(
                        onClick = alRegenerar,
                        enabled = !estado.cargando && !estado.confirmando,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Generar nuevo QR") }
                }
            }
        }
    }
}

@Composable
private fun SelloSimulacion() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$ETIQUETA_SIMULACION · pago académico, no es un pago real",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ImagenQrSimulado(contenido: String) {
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
                contentDescription = "Código QR de pago ($ETIQUETA_SIMULACION)",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("No se pudo dibujar el QR", color = Color.Black, textAlign = TextAlign.Center)
        }
    }
}
