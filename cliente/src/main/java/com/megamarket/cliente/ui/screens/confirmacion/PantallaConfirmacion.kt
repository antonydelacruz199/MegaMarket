package com.megamarket.cliente.ui.screens.confirmacion

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.R
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.state.ConfirmacionUiState
import com.megamarket.cliente.ui.components.BarraSuperior
import com.megamarket.cliente.ui.components.EstadoVacio
import com.megamarket.cliente.viewmodel.ConfirmacionViewModel
import com.megamarket.modelo.formatearSoles

@Composable
fun PantallaConfirmacion(
    viewModel: ConfirmacionViewModel,
    alIrAlInicio: () -> Unit,
    alPagar: (String) -> Unit = {},
    alPagarConQr: (String) -> Unit = alPagar
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var yaMostrado by remember { mutableStateOf(false) }

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
                        alPagar = alPagar,
                        alPagarConQr = alPagarConQr,
                        alCancelar = viewModel::cancelarPedido,
                        alSincronizar = viewModel::sincronizarYRecargar,
                        alAceptarPrecios = viewModel::aceptarPrecios,
                        alReintentarEnvio = viewModel::reintentarEnvio,
                        alCancelarRevision = viewModel::cancelarPedido
                    )
                }
                estado.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
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

@Composable
private fun ResumenYAcciones(
    estado: ConfirmacionUiState,
    alPagar: (String) -> Unit,
    alPagarConQr: (String) -> Unit,
    alCancelar: () -> Unit,
    alSincronizar: () -> Unit,
    alAceptarPrecios: () -> Unit,
    alReintentarEnvio: () -> Unit,
    alCancelarRevision: () -> Unit
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
            estado.pagoConfirmado == true || pedido.estado == "PAGADO" -> "Pedido pagado"
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
    Text("${pedido.cantidadProductos} producto(s)")
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

        pedido.estado == "PAGADO" ||
            (estado.esOnline && estado.pagoConfirmado == true) -> {
            Text(
                "Pago confirmado. El pedido pasará a preparación.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        estado.listoParaPagarOnline || estado.listoParaPagarContraentrega -> {
            val remoto = pedido.remoteId!!.trim()
            Text(
                if (estado.listoParaPagarContraentrega) {
                    "Puedes pagar ahora con billetera, o el repartidor cobrará al entregar (efectivo o QR)."
                } else {
                    "Para completar la compra elige una opción de pago."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { alPagar(remoto) },
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Realizar pago") }
            if (estado.listoParaPagarOnline) {
                OutlinedButton(
                    onClick = { alPagarConQr(remoto) },
                    enabled = !estado.cancelando,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Pagar con QR") }
            }
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
                    "El pedido aún no está en el servidor. Actualiza para continuar."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = alSincronizar,
                enabled = !estado.sincronizando && !estado.cancelando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (estado.sincronizando) "Sincronizando…" else "Actualizar pedido")
            }
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando && !estado.sincronizando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        estado.enPreparacion -> {
            Text(
                if (estado.esOnline) {
                    "El picker está preparando tu pedido. Cuando quede CONFIRMADO podrás pagar en línea."
                } else {
                    "El picker está preparando tu pedido. Pagarás al recibir (contraentrega)."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = alSincronizar,
                enabled = !estado.sincronizando && !estado.cancelando,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Actualizar estado") }
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando && !pedido.remoteId.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        else -> {
            // CONTRAENTREGA ya CONFIRMADO/LISTO u otros: no exige cobro inmediato.
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
