package com.megamarket.cliente.ui.screens.pedidos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.EtapaProgresoPedido
import com.megamarket.cliente.data.repository.LineaPedidoCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente
import com.megamarket.cliente.data.repository.formatearTiempoRestante
import com.megamarket.cliente.data.repository.importeClienteCentimos
import com.megamarket.cliente.data.repository.milisRestantesHasta
import com.megamarket.cliente.ui.components.ImagenProducto
import com.megamarket.cliente.viewmodel.DetallePedidoViewModel
import com.megamarket.modelo.formatearSoles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetallePedido(
    viewModel: DetallePedidoViewModel,
    alVolver: () -> Unit,
    alCerrarSesion: () -> Unit,
    alPagarQr: (String) -> Unit = {}
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> viewModel.iniciarSeguimiento()
                Lifecycle.Event.ON_STOP -> viewModel.detenerSeguimiento()
                else -> Unit
            }
        }
        ciclo.lifecycle.addObserver(obs)
        onDispose {
            ciclo.lifecycle.removeObserver(obs)
            viewModel.detenerSeguimiento()
        }
    }
    LaunchedEffect(estado.sesionVencida) {
        if (estado.sesionVencida) alCerrarSesion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del pedido") },
                navigationIcon = {
                    IconButton(onClick = alVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            estado.cargando && estado.detalle == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }
            }
            estado.error != null && estado.detalle == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = viewModel::cargar) { Text("Reintentar") }
                }
            }
            estado.detalle != null -> {
                val d = estado.detalle!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Pedido ${d.id.take(8)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(d.fecha ?: "Sin fecha")
                    Text("Estado: ${d.estado}", fontWeight = FontWeight.SemiBold)
                    Text(d.totalCentimos.formatearSoles())
                    if (estado.error != null) {
                        Text(estado.error!!, color = MaterialTheme.colorScheme.error)
                    }

                    Text("Seguimiento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    d.etapasProgreso().forEach { etapa ->
                        FilaEtapa(etapa)
                    }

                    Text("Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    d.lineas.forEach { linea ->
                        FilaProducto(linea)
                    }

                    val faltantes = d.lineas.filter { it.estadoPicking.equals("FALTANTE", true) }
                    if (faltantes.isNotEmpty()) {
                        Text("Faltantes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        faltantes.forEach { Text("• ${it.nombre} (x${it.cantidad})") }
                    }

                    if (d.sustituciones.isNotEmpty()) {
                        Text("Sustituciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        d.sustituciones.forEach { prop ->
                            BloqueSustitucion(
                                propuesta = prop,
                                ahora = estado.ahoraEpochMs,
                                decidiendo = estado.decidiendoId == prop.id,
                                alAceptar = { viewModel.aceptar(prop.id) },
                                alRechazar = { viewModel.rechazar(prop.id) }
                            )
                        }
                    }

                    Text("Pago", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val pago = d.pago
                    if (pago == null) {
                        Text("Sin información de pago")
                    } else {
                        Text("Modalidad: ${d.modalidadVisible}")
                        Text("Estado: ${pago.estado ?: "-"}")
                        pago.montoCentimos?.let { Text("Monto: ${it.formatearSoles()}") }
                        pago.numeroOperacionBanco?.let { Text("Operación: $it") }
                        if (d.puedePagarConQr) {
                            Text(
                                if (pago.modalidad.equals("CONTRAENTREGA", ignoreCase = true)) {
                                    "Puedes pagar con billetera ahora, o el repartidor cobrará al entregar."
                                } else {
                                    "El pago aún no está confirmado. Completa el cobro para despachar."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { alPagarQr(d.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Realizar pago") }
                        }
                        if (d.puedeAutorizarBilletera) {
                            Text(
                                "Si pagarás con billetera al recibir, autoriza el débito para el repartidor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (estado.autorizacionBilleteraOk) {
                                Text(
                                    "Autorización registrada. El repartidor puede cobrar con billetera.",
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                OutlinedButton(
                                    onClick = viewModel::autorizarBilleteraContraentrega,
                                    enabled = !estado.autorizandoBilletera,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (estado.autorizandoBilletera) {
                                            "Autorizando…"
                                        } else {
                                            "Autorizar cobro con billetera"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (d.puedeCancelar && !estado.cancelado) {
                        OutlinedButton(
                            onClick = viewModel::cancelarPedido,
                            enabled = !estado.cancelando,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (estado.cancelando) "Cancelando…" else "Cancelar pedido")
                        }
                    }
                    if (estado.cancelado || d.estado.equals("CANCELADO", true)) {
                        Text(
                            "Pedido cancelado.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text("Entrega", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(d.franjaVisible)
                    val dir = d.direccion
                    if (dir != null) {
                        Text(listOfNotNull(dir.direccion, dir.distrito, dir.provincia, dir.departamento)
                            .filter { it.isNotBlank() }
                            .joinToString(", "))
                        dir.telefono?.let { Text("Tel: $it") }
                    } else {
                        Text("Sin dirección registrada")
                    }
                    if (d.estado.equals("EN_CAMINO", true) || d.estado.equals("ENTREGADO", true)) {
                        Text("Estado de entrega: ${d.estado}")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaEtapa(etapa: EtapaProgresoPedido) {
    val marca = when {
        etapa.actual -> "●"
        etapa.completada -> "✓"
        else -> "○"
    }
    Text(
        text = "$marca ${etapa.nombre}",
        fontWeight = if (etapa.actual) FontWeight.Bold else FontWeight.Normal,
        color = when {
            etapa.actual -> MaterialTheme.colorScheme.primary
            etapa.completada -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun FilaProducto(linea: LineaPedidoCliente) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(linea.nombre, fontWeight = FontWeight.Medium)
        Text("Cantidad: ${linea.cantidad} · ${linea.precioUnitarioCentimos.formatearSoles()}")
        Text("Subtotal: ${linea.subtotalCentimos.formatearSoles()}")
        if (!linea.estadoPicking.isNullOrBlank()) {
            Text("Preparación: ${linea.estadoPicking}" +
                (linea.cantidadArmada?.let { " ($it armada)" } ?: ""))
        }
    }
}

@Composable
private fun BloqueSustitucion(
    propuesta: PropuestaSustitucionCliente,
    ahora: Long,
    decidiendo: Boolean,
    alAceptar: () -> Unit,
    alRechazar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Estado: ${propuesta.estado}", fontWeight = FontWeight.SemiBold)
        if (propuesta.respuestaAutomatica && EstadoSustitucion.esDefinitivo(propuesta.estado)) {
            Text(
                "Resuelta automáticamente según tu preferencia",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImagenProducto(
                solicitud = propuesta.imagenOriginal,
                modifier = Modifier.size(56.dp),
                descripcion = propuesta.nombreOriginal
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(propuesta.nombreOriginal, modifier = Modifier.weight(1f))
            Text("→")
            Spacer(modifier = Modifier.width(8.dp))
            ImagenProducto(
                solicitud = propuesta.imagenSustituto,
                modifier = Modifier.size(56.dp),
                descripcion = propuesta.nombreSustituto
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(propuesta.nombreSustituto, modifier = Modifier.weight(1f))
        }
        Text("Cantidad: ${propuesta.cantidad}")
        Text(
            "Máximo a pagar: ${
                importeClienteCentimos(
                    propuesta.precioOriginalCentimos,
                    propuesta.precioSustitutoCentimos,
                    propuesta.cantidad
                ).formatearSoles()
            }"
        )
        if (EstadoSustitucion.esPendiente(propuesta.estado)) {
            val resto = milisRestantesHasta(propuesta.fechaLimite, ahora)
            Text("Tiempo: ${formatearTiempoRestante(resto)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = alAceptar, enabled = !decidiendo) {
                    Text(if (decidiendo) "…" else "Aceptar")
                }
                OutlinedButton(onClick = alRechazar, enabled = !decidiendo) {
                    Text("Rechazar")
                }
            }
        }
    }
}
