package com.megamarket.cliente.ui.screens.confirmacion

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.PedidoDetalle
import com.megamarket.cliente.state.ConfirmacionUiState
import com.megamarket.cliente.ui.components.BarraSuperior
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.viewmodel.ConfirmacionViewModel
import com.megamarket.modelo.formatearSoles

private val VerdePagoQr = Color(0xFF2E7D32)

@Composable
fun PantallaConfirmacion(
    viewModel: ConfirmacionViewModel,
    alIrAlInicio: () -> Unit,
    alPagarConQr: (String) -> Unit = {},
    alEscanearQr: (String) -> Unit = {},
    alSeguirPedido: (String) -> Unit = {},
    pagoRecienConfirmado: Boolean = false,
    alConsumirPagoConfirmado: () -> Unit = {}
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var yaMostrado by remember { mutableStateOf(false) }

    LaunchedEffect(pagoRecienConfirmado) {
        if (pagoRecienConfirmado) {
            viewModel.marcarPagoRealizado()
            alConsumirPagoConfirmado()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (yaMostrado) {
            viewModel.sincronizarYRecargar()
        } else {
            yaMostrado = true
        }
        onPauseOrDispose { }
    }
    val pedido = estado.pedido

    Scaffold(topBar = { BarraSuperior(titulo = stringResource(R.string.confirmacion)) }) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
        ) {
            if (estado.sincronizando || estado.cancelando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    estado.cargando && pedido == null -> CircularProgressIndicator()
                    pedido == null -> EstadoVacio(
                        icono = Icons.Default.Warning,
                        titulo = estado.error ?: "No hay una compra para mostrar",
                        descripcion = "Puedes revisar tu carrito desde el inicio.",
                        etiquetaAccion = "Reintentar",
                        alPulsarAccion = { viewModel.cargarPedido(forzarSincronizacion = true) }
                    )
                    else -> ResumenYAcciones(
                        estado = estado,
                        alPagarConQr = alPagarConQr,
                        alEscanearQr = alEscanearQr,
                        alCancelar = viewModel::cancelarPedido,
                        alAceptarPrecios = viewModel::aceptarPrecios,
                        alReintentarEnvio = viewModel::reintentarEnvio,
                        alCancelarRevision = viewModel::cancelarPedido,
                        alSeguirPedido = {
                            val remoto = pedido.remoteId?.trim().orEmpty()
                            if (remoto.isNotEmpty()) alSeguirPedido(remoto)
                            else alIrAlInicio()
                        }
                    )
                }
                estado.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                if (!estado.pagoRealizado) {
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = alIrAlInicio,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.volver_inicio)) }
                }
            }
        }
    }
}

@Composable
private fun ResumenYAcciones(
    estado: ConfirmacionUiState,
    alPagarConQr: (String) -> Unit,
    alEscanearQr: (String) -> Unit,
    alCancelar: () -> Unit,
    alAceptarPrecios: () -> Unit,
    alReintentarEnvio: () -> Unit,
    alCancelarRevision: () -> Unit,
    alSeguirPedido: () -> Unit
) {
    val pedido = checkNotNull(estado.pedido)
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = when {
            estado.pagoRealizado -> "Pago realizado"
            pedido.estado == "CANCELADO" -> "Pedido cancelado"
            pedido.estado == "REQUIERE_REVISION" -> "Pedido requiere revisión"
            estado.listoParaPagarOnline || estado.listoParaPagarContraentrega -> "Completa tu compra"
            estado.enPreparacion -> "Pedido en preparación"
            pedido.estado == Pedido.ESTADO_CONFIRMADO -> "Pedido confirmado"
            pedido.estado == "PENDIENTE_CONFIRMACION" -> "Pedido pendiente de envío"
            else -> "Pedido registrado"
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = "Pedido N.° ${pedido.id}",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Total ${pedido.totalCentimos.formatearSoles()}",
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(12.dp))
    ListaProductosPedido(pedido.detalles)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "${pedido.direccion.direccion}, ${pedido.direccion.distrito}",
        textAlign = TextAlign.Center
    )
    Text(
        text = "${pedido.direccion.provincia}, ${pedido.direccion.departamento}",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text("Tel. ${pedido.direccion.telefono}")
    pedido.franjaEtiqueta?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Entrega: $it", textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
    pedido.modalidadEtiqueta?.let {
        Text("Pago: $it", textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(16.dp))

    when {
        pedido.estado == "REQUIERE_REVISION" -> {
            Text(
                "El servidor no registró el pedido. Puedes reintentar el envío o aceptar un total actualizado si cambió el precio.",
                textAlign = TextAlign.Center
            )
            Button(
                onClick = alReintentarEnvio,
                enabled = !estado.cancelando && !estado.sincronizando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Reintentar envío") }
            estado.totalPropuesto?.let { total ->
                Text("Total actualizado: ${total.formatearSoles()}")
                OutlinedButton(
                    onClick = alAceptarPrecios,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Aceptar total y reintentar") }
            }
            OutlinedButton(
                onClick = alCancelarRevision,
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar pedido") }
        }

        pedido.estado == "CANCELADO" -> {
            Text("Este pedido fue cancelado.", textAlign = TextAlign.Center)
        }

        estado.pagoRealizado -> {
            Text(
                "Tu pago se registró correctamente. Puedes rastrear el proceso de tu pedido.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = alSeguirPedido,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Rastrear Pedido") }
        }

        estado.listoParaPagarOnline -> {
            val remoto = pedido.remoteId!!.trim()
            Text(
                "Tu pedido ya está confirmado. Continúa con el pago en línea.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { alPagarConQr(remoto) },
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VerdePagoQr,
                    contentColor = Color.White
                )
            ) { Text("Pago con QR") }
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        estado.listoParaPagarContraentrega -> {
            val remoto = pedido.remoteId!!.trim()
            Text(
                "En la entrega el repartidor cobrará en efectivo o te mostrará un QR para pagar con billetera.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { alEscanearQr(remoto) },
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VerdePagoQr,
                    contentColor = Color.White
                )
            ) { Text("Escanear QR") }
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        estado.esperandoSincronizacionPago -> {
            Text(
                if (estado.sincronizando) {
                    "Registrando el pedido en el servidor…"
                } else {
                    "El pedido aún no está en el servidor. Se actualizará automáticamente."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando && !estado.sincronizando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        estado.enPreparacion -> {
            Text(
                if (estado.esOnline) {
                    "El picker está preparando tu pedido. Cuando quede confirmado aparecerá el pago con QR."
                } else {
                    "El picker está preparando tu pedido. Pagarás al recibir (contraentrega)."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando && !pedido.remoteId.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        else -> {
            Text(
                "No hace falta pagar ahora. Pagarás al recibir el pedido.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            if (pedido.estado in setOf("RECIBIDO", "EN_PICKING", Pedido.ESTADO_CONFIRMADO) &&
                estado.pagoConfirmado != true
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = alCancelar,
                    enabled = !estado.cancelando && !pedido.remoteId.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
            }
        }
    }
}

@Composable
private fun ListaProductosPedido(detalles: List<PedidoDetalle>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Productos",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (detalles.isEmpty()) {
            Text(
                "Sin detalle de productos",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            detalles.forEach { detalle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(detalle.nombreProducto, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${detalle.cantidad} × ${detalle.precioUnitarioCentimos.formatearSoles()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        detalle.subtotalCentimos.formatearSoles(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
