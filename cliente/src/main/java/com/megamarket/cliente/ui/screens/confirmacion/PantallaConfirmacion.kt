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
import com.megamarket.cliente.data.repository.MODALIDAD_ONLINE
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
    alPagar: (String) -> Unit = {}
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
                        alCancelar = viewModel::cancelarPedido,
                        alSincronizar = viewModel::sincronizarYRecargar,
                        alAceptarPrecios = viewModel::aceptarPrecios,
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
    alCancelar: () -> Unit,
    alSincronizar: () -> Unit,
    alAceptarPrecios: () -> Unit,
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
        text = when (pedido.estado) {
            "CONFIRMADO" -> "Compra confirmada"
            "CANCELADO" -> "Pedido cancelado"
            "PAGADO" -> "Pedido pagado"
            "REQUIERE_REVISION" -> "Pedido requiere revisión"
            else -> "Pedido pendiente de confirmación"
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
                "El servidor no confirmó el pedido. Revisa stock y precios antes de reintentarlo.",
                textAlign = TextAlign.Center
            )
            estado.totalPropuesto?.let { total ->
                Text("Total actualizado: ${total.formatearSoles()}")
                Button(
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

        pedido.estado == "PENDIENTE_CONFIRMACION" ||
            (pedido.modalidadPago.equals(MODALIDAD_ONLINE, true) &&
                pedido.estado == Pedido.ESTADO_CONFIRMADO &&
                pedido.remoteId.isNullOrBlank()) -> {
            Text(
                if (estado.sincronizando) {
                    "Sincronizando el pedido con el servidor…"
                } else {
                    "El pedido aún no está listo en el servidor. Sincroniza para pagar o cancelar."
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
            ) { Text("Sincronizar y actualizar") }
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando && !estado.sincronizando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        pedido.estado == "PAGADO" ||
            (pedido.modalidadPago.equals(MODALIDAD_ONLINE, true) && estado.pagoConfirmado == true) -> {
            Text(
                "Pago confirmado. El pedido pasará a preparación.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        pedido.modalidadPago.equals(MODALIDAD_ONLINE, true) &&
            pedido.estado == Pedido.ESTADO_CONFIRMADO &&
            estado.pagoConfirmado != true -> {
            val remoto = pedido.remoteId?.trim().orEmpty()
            Text(
                "Elige si deseas pagar ahora o cancelar el pedido.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { if (remoto.isNotEmpty()) alPagar(remoto) },
                enabled = remoto.isNotEmpty() && !estado.cancelando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Pagar ahora") }
            OutlinedButton(
                onClick = alCancelar,
                enabled = !estado.cancelando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido") }
        }

        else -> {
            // CONTRAENTREGA u otros: no exige cobro inmediato.
            Text(
                "No hace falta pagar ahora. Pagarás al recibir el pedido.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            if (pedido.estado == Pedido.ESTADO_CONFIRMADO && estado.pagoConfirmado != true) {
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
